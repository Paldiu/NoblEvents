package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventSubscription;
import app.simplexdev.noblevents.impl.SimpleEventSubscription;
import app.simplexdev.noblevents.scheduler.BukkitSchedulers;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent pipeline builder for a single Bukkit event type. Wraps a {@link Flux} and exposes
 * domain-specific operators so callers never need to import Reactor directly for common cases.
 *
 * <p>Each operator returns a <em>new</em> {@code EventStream}; the original is unmodified and
 * can be safely shared across threads or reused as a base for multiple pipelines.
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
    private final Flux<E> flux;

    EventStream(Class<E> eventType, Flux<E> flux, Plugin plugin) {
        this.eventType = eventType;
        this.flux = flux;
        this.plugin = plugin;
    }

    public EventStream<E> filter(Predicate<? super E> predicate) {
        return new EventStream<>(eventType, flux.filter(predicate), plugin);
    }

    /** Ignores events where {@link org.bukkit.event.Cancellable#isCancelled()} is true. */
    public EventStream<E> ignoreCancelled() {
        return new EventStream<>(eventType, flux.filter(e -> {
            if (e instanceof org.bukkit.event.Cancellable c) return !c.isCancelled();
            return true;
        }), plugin);
    }

    /**
     * True debounce: only emits an event after {@code delay} of silence.
     * Suppresses burst spam — only the last event in a rapid burst is emitted.
     */
    public EventStream<E> debounce(Duration delay) {
        return new EventStream<>(eventType, flux.sampleTimeout(e -> Mono.delay(delay)), plugin);
    }

    /**
     * Emits the most-recent event at fixed {@code period} intervals.
     * Useful when you want periodic snapshots rather than every event.
     */
    public EventStream<E> throttle(Duration period) {
        return new EventStream<>(eventType, flux.sample(period), plugin);
    }

    /** Auto-cancels the subscription after {@code count} events. */
    public EventStream<E> limit(long count) {
        return new EventStream<>(eventType, flux.take(count), plugin);
    }

    /** Convenience for {@code limit(1)} — subscribe to the very next occurrence. */
    public EventStream<E> once() {
        return limit(1);
    }

    /**
     * Terminates the stream with an error if no event arrives within {@code timeout}.
     * Pair with the three-arg {@code subscribe} overload to handle the timeout case.
     */
    public EventStream<E> timeout(Duration timeout) {
        return new EventStream<>(eventType, flux.timeout(timeout), plugin);
    }

    /**
     * Maps each event to another Event type. For non-Event transformations
     * (e.g. extracting a {@code Player} from a {@code PlayerMoveEvent}), use
     * {@link #flux()}{@code .map(...)} instead.
     */
    public <R extends Event> EventStream<R> map(Function<? super E, ? extends R> mapper, Class<R> resultType) {
        return new EventStream<>(resultType, flux.map(mapper), plugin);
    }

    /**
     * Delivers events on Bukkit's main server thread. Safe for all Bukkit API calls.
     * Note: Bukkit events already arrive on the main thread; only use this after
     * {@link #onAsync()} to switch back, or when an intermediate operator may shift threads.
     */
    public EventStream<E> onMainThread() {
        return new EventStream<>(eventType, flux.publishOn(BukkitSchedulers.mainThread(plugin)), plugin);
    }

    /** Delivers events on Bukkit's async thread pool. Do not call Bukkit API here. */
    public EventStream<E> onAsync() {
        return new EventStream<>(eventType, flux.publishOn(BukkitSchedulers.async(plugin)), plugin);
    }

    /**
     * Subscribes to events, logging any uncaught subscriber errors to the plugin logger
     * rather than letting them propagate into Reactor's global error hook.
     */
    public EventSubscription subscribe(Consumer<? super E> onEvent) {
        Objects.requireNonNull(onEvent, "onEvent must not be null");
        return subscribe(onEvent, err ->
            plugin.getLogger().severe("[NoblEvents] Unhandled error in subscriber for "
                + eventType.getSimpleName() + ": " + err.getMessage()));
    }

    public EventSubscription subscribe(Consumer<? super E> onEvent, Consumer<Throwable> onError) {
        Objects.requireNonNull(onEvent, "onEvent must not be null");
        Objects.requireNonNull(onError, "onError must not be null");
        return new SimpleEventSubscription(flux.subscribe(onEvent, onError), eventType);
    }

    public EventSubscription subscribe(
            Consumer<? super E> onEvent,
            Consumer<Throwable> onError,
            Runnable onComplete) {
        Objects.requireNonNull(onEvent, "onEvent must not be null");
        Objects.requireNonNull(onError, "onError must not be null");
        Objects.requireNonNull(onComplete, "onComplete must not be null");
        return new SimpleEventSubscription(flux.subscribe(onEvent, onError, onComplete), eventType);
    }

    /**
     * Returns the underlying {@link Flux} for operators not exposed by this builder —
     * e.g. {@code bufferTimeout}, {@code window}, {@code zip}, etc.
     */
    public Flux<E> flux() {
        return flux;
    }
}
