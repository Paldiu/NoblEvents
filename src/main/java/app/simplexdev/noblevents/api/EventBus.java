package app.simplexdev.noblevents.api;

import org.bukkit.event.Event;
import reactor.core.publisher.Flux;

public interface EventBus {

    /**
     * Returns a cold-starts-hot {@link Flux} that emits every Bukkit event of the given type.
     * The underlying Bukkit listener is registered lazily on the first subscriber.
     */
    <E extends Event> Flux<E> of(Class<E> eventType);
}
