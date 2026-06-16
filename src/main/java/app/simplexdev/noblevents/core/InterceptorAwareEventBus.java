package app.simplexdev.noblevents.core;

import app.simplexdev.noblevents.api.EventBus;
import org.bukkit.event.Event;
import reactor.core.publisher.Flux;

/**
 * Wraps any {@link EventBus} and runs every emission through an {@link InterceptorChain}
 * before it reaches subscribers. Interceptors that call {@link
 * app.simplexdev.noblevents.api.interceptor.InterceptorContext#cancel()} suppress the event
 * from all downstream subscribers without affecting the underlying Bukkit event.
 */
public final class InterceptorAwareEventBus implements EventBus {

    private final EventBus delegate;
    private final InterceptorChain chain;

    public InterceptorAwareEventBus(EventBus delegate, InterceptorChain chain) {
        this.delegate = delegate;
        this.chain = chain;
    }

    @Override
    public <E extends Event> Flux<E> of(Class<E> eventType) {
        return delegate.of(eventType)
            .filter(e -> !chain.process(e).isCancelled());
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
