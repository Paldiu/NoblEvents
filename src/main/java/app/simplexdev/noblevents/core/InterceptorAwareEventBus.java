package app.simplexdev.noblevents.core;

import org.bukkit.event.Event;
import reactor.core.publisher.Flux;

/**
 * Wraps an {@link AbstractEventBus} and runs every emission through an {@link InterceptorChain}
 * before it reaches subscribers. Interceptors that call {@link
 * app.simplexdev.noblevents.api.interceptor.InterceptorContext#cancel()} suppress the event
 * from all downstream subscribers without affecting the underlying Bukkit event.
 */
public final class InterceptorAwareEventBus implements app.simplexdev.noblevents.api.EventBus {

    private final AbstractEventBus delegate;
    private final InterceptorChain chain;

    public InterceptorAwareEventBus(AbstractEventBus delegate, InterceptorChain chain) {
        this.delegate = delegate;
        this.chain = chain;
    }

    @Override
    public <E extends Event> Flux<E> of(Class<E> eventType) {
        return delegate.of(eventType)
            .filter(e -> !chain.process(e).isCancelled());
    }

    public void shutdown() {
        delegate.shutdown();
    }
}
