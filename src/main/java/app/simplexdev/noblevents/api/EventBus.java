package app.simplexdev.noblevents.api;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import reactor.core.publisher.Flux;

public interface EventBus {

    /**
     * Returns a {@link Flux} that emits every Bukkit event of the given type at
     * {@link EventPriority#NORMAL} priority, including already-cancelled events.
     * The underlying Bukkit listener is registered lazily on the first subscriber
     * and unregistered automatically when the last subscriber leaves.
     */
    <E extends Event> Flux<E> of(Class<E> eventType);

    /**
     * Returns a {@link Flux} that emits Bukkit events of the given type at the specified
     * {@code priority}. When {@code ignoreCancelled} is {@code true}, events that are already
     * cancelled by the time this listener runs are suppressed from the stream.
     *
     * <p>Each unique {@code (eventType, priority, ignoreCancelled)} combination registers a
     * distinct Bukkit listener and maintains its own subscriber reference count for teardown.
     */
    <E extends Event> Flux<E> of(Class<E> eventType, EventPriority priority, boolean ignoreCancelled);
}
