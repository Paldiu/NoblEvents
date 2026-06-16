package app.simplexdev.noblevents.core;

import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.api.interceptor.InterceptorContext;
import app.simplexdev.noblevents.api.interceptor.Intercepts;
import org.bukkit.event.Event;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InterceptorChain {

    private final CopyOnWriteArrayList<EventInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public void register(EventInterceptor interceptor) {
        interceptors.add(interceptor);
        interceptors.sort(Comparator.comparingInt(i -> {
            final Intercepts ann = i.getClass().getAnnotation(Intercepts.class);
            return ann != null ? ann.priority() : 0;
        }));
    }

    public void unregister(EventInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    public <E extends Event> InterceptorContext<E> process(E event) {
        final InterceptorContext<E> ctx = new InterceptorContext<>(event);
        for (final EventInterceptor interceptor : interceptors) {
            if (ctx.isCancelled()) break;
            if (!handles(interceptor, event)) continue;
            interceptor.intercept(ctx);
        }
        return ctx;
    }

    public List<EventInterceptor> interceptors() {
        return List.copyOf(interceptors);
    }

    private static boolean handles(EventInterceptor interceptor, Event event) {
        final Intercepts ann = interceptor.getClass().getAnnotation(Intercepts.class);
        if (ann == null || ann.value().length == 0) return true;
        for (final Class<? extends Event> type : ann.value()) {
            if (type.isInstance(event)) return true;
        }
        return false;
    }
}
