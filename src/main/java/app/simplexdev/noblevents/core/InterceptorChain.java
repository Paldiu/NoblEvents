package app.simplexdev.noblevents.core;

import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.api.interceptor.InterceptorContext;
import app.simplexdev.noblevents.api.interceptor.Intercepts;
import org.bukkit.event.Event;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InterceptorChain {

    private final CopyOnWriteArrayList<EventInterceptor> interceptors = new CopyOnWriteArrayList<>();
    private final Map<EventInterceptor, Class<? extends Event>[]> handledTypes = new ConcurrentHashMap<>();
    private final Map<EventInterceptor, Integer> priorities = new ConcurrentHashMap<>();
    private final Logger logger;

    public InterceptorChain(Logger logger) {
        this.logger = logger;
    }

    public InterceptorChain() {
        this(LoggerFactory.getLogger(InterceptorChain.class));
    }

    @SuppressWarnings("unchecked")
    public void register(EventInterceptor interceptor) {
        final Intercepts ann = interceptor.getClass().getAnnotation(Intercepts.class);
        handledTypes.put(interceptor, ann != null ? ann.value() : new Class[0]);
        priorities.put(interceptor, ann != null ? ann.priority() : 0);
        interceptors.add(interceptor);
        interceptors.sort(Comparator.comparingInt((EventInterceptor i) -> priorities.getOrDefault(i, 0)).reversed());
    }

    public void unregister(EventInterceptor interceptor) {
        interceptors.remove(interceptor);
        handledTypes.remove(interceptor);
        priorities.remove(interceptor);
    }

    public <E extends Event> InterceptorContext<E> process(E event) {
        final InterceptorContext<E> ctx = new InterceptorContext<>(event);
        for (final EventInterceptor interceptor : interceptors) {
            if (ctx.isCancelled()) break;
            if (!handles(interceptor, event)) continue;
            try {
                interceptor.intercept(ctx);
            } catch (Exception ex) {
                logger.warn("[NoblEvents] Interceptor {} threw for event {}: {}",
                    interceptor.getClass().getSimpleName(), event.getEventName(), ex.getMessage());
            }
        }
        return ctx;
    }

    public List<EventInterceptor> interceptors() {
        return List.copyOf(interceptors);
    }

    private boolean handles(EventInterceptor interceptor, Event event) {
        final Class<? extends Event>[] types = handledTypes.get(interceptor);
        if (types == null || types.length == 0) return true;
        for (final Class<? extends Event> type : types) {
            if (type.isInstance(event)) return true;
        }
        return false;
    }
}
