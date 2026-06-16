package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventSubscription;
import app.simplexdev.noblevents.impl.SimpleEventSubscription;
import app.simplexdev.noblevents.scheduler.BukkitSchedulers;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Fluent pipeline builder for a single Bukkit event type. Wraps a {@link Flux} and exposes
 * domain-specific operators so callers never need to import Reactor directly for common cases.
 *
 * <p>Obtain an instance via {@link NoblEvents#events(Class)}.
 *
 * <pre>{@code
 * NoblEvents.events(PlayerMoveEvent.class)
 *     .filter(e -> e.getPlayer().getHealth() < 5)
 *     .debounce(Duration.ofMillis(200))
 *     .onMainThread()
 *     .subscribe(e -> sendWarning(e.getPlayer()));
 * }</pre>
 *
 * <p>For operators not exposed here (windowing, zipping, etc.) call {@link #flux()} to get
 * the underlying {@link Flux} and compose freely.
 */
public final class EventStream<E extends Event> {

    private final Class<E> eventType;
    private final Plugin plugin;
    private Flux<E> flux;

    EventStream(Class<E> eventType, Flux<E> flux, Plugin plugin) {
        this.eventType = eventType;
        this.flux = flux;
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Filtering
    // -------------------------------------------------------------------------

    public EventStream<E> filter(Predicate<? super E> predicate) {
        flux = flux.filter(predicate);
        return this;
    }

    /** Ignores events where {@link org.bukkit.event.Cancellable#isCancelled()} is true. */
    public EventStream<E> ignoreCancelled() {
        flux = flux.filter(e -> {
            if (e instanceof org.bukkit.event.Cancellable c) return !c.isCancelled();
            return true;
        });
        return this;
    }

    // -------------------------------------------------------------------------
    // Rate control
    // -------------------------------------------------------------------------

    /**
     * True debounce: only emits an event after {@code delay} of silence.
     * Suppresses burst spam — only the last event in a rapid burst is emitted.
     */
    public EventStream<E> debounce(Duration delay) {
        flux = flux.sampleTimeout(e -> Mono.delay(delay));
        return this;
    }

    /**
     * Emits the most-recent event at fixed {@code period} intervals.
     * Useful when you want periodic snapshots rather than every event.
     */
    public EventStream<E> throttle(Duration period) {
        flux = flux.sample(period);
        return this;
    }

    // -------------------------------------------------------------------------
    // Quantity
    // -------------------------------------------------------------------------

    /** Auto-cancels the subscription after {@code count} events. */
    public EventStream<E> limit(long count) {
        flux = flux.take(count);
        return this;
    }

    /** Convenience for {@code limit(1)} — subscribe to the very next occurrence. */
    public EventStream<E> once() {
        return limit(1);
    }

    // -------------------------------------------------------------------------
    // Thread routing
    // -------------------------------------------------------------------------

    /** Delivers events on Bukkit's main server thread. Safe for all Bukkit API calls. */
    public EventStream<E> onMainThread() {
        flux = flux.publishOn(BukkitSchedulers.mainThread(plugin));
        return this;
    }

    /** Delivers events on Bukkit's async thread pool. Do not call Bukkit API here. */
    public EventStream<E> onAsync() {
        flux = flux.publishOn(BukkitSchedulers.async(plugin));
        return this;
    }

    // -------------------------------------------------------------------------
    // Terminal — subscribe
    // -------------------------------------------------------------------------

    public EventSubscription subscribe(Consumer<? super E> onEvent) {
        return new SimpleEventSubscription(flux.subscribe(onEvent), eventType);
    }

    public EventSubscription subscribe(Consumer<? super E> onEvent, Consumer<Throwable> onError) {
        return new SimpleEventSubscription(flux.subscribe(onEvent, onError), eventType);
    }

    public EventSubscription subscribe(
            Consumer<? super E> onEvent,
            Consumer<Throwable> onError,
            Runnable onComplete) {
        return new SimpleEventSubscription(flux.subscribe(onEvent, onError, onComplete), eventType);
    }

    // -------------------------------------------------------------------------
    // Escape hatch
    // -------------------------------------------------------------------------

    /**
     * Returns the underlying {@link Flux} for operators not exposed by this builder —
     * e.g. {@code bufferTimeout}, {@code window}, {@code zip}, etc.
     */
    public Flux<E> flux() {
        return flux;
    }
}
