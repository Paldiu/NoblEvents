package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventSubscription;
import app.simplexdev.noblevents.impl.SimpleEventSubscription;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A plugin-scoped event subscription manager. Every subscription created through a
 * {@link BoundStream} returned by {@link #events} is tracked here and automatically
 * cancelled when the owning plugin disables (or when {@link #cancelAll()} is called
 * explicitly). Subscriptions that complete naturally (e.g. {@code once()}, {@code limit()})
 * remove themselves from tracking via {@code doFinally}, so the managed list stays lean.
 *
 * <p>Obtain an instance via {@link NoblEvents#forPlugin(Plugin)}.
 *
 * <pre>{@code
 * PluginEventContext ctx = NoblEvents.forPlugin(this);
 *
 * ctx.events(PlayerMoveEvent.class)
 *     .filter(e -> e.getPlayer().getHealth() < 5)
 *     .onMainThread()
 *     .subscribe(e -> sendWarning(e.getPlayer()));
 * }</pre>
 */
public final class PluginEventContext {

    private final Plugin owner;
    private final List<EventSubscription> managed = new CopyOnWriteArrayList<>();

    PluginEventContext(Plugin owner) {
        this.owner = owner;
    }

    /**
     * Returns a fluent stream builder for the given event type at {@link EventPriority#NORMAL}.
     * Subscriptions created via the returned {@link BoundStream} are tracked by this context.
     */
    public <E extends Event> BoundStream<E> events(Class<E> eventType) {
        NoblEvents.requireEnabled();
        return new BoundStream<>(NoblEvents.events(eventType));
    }

    /**
     * Returns a fluent stream builder for the given event type at the specified priority.
     * Subscriptions created via the returned {@link BoundStream} are tracked by this context.
     */
    public <E extends Event> BoundStream<E> events(Class<E> eventType, EventPriority priority, boolean ignoreCancelled) {
        NoblEvents.requireEnabled();
        return new BoundStream<>(NoblEvents.events(eventType, priority, ignoreCancelled));
    }

    /**
     * Cancels all tracked subscriptions. Called automatically when the owning plugin
     * disables; also available for manual early teardown.
     */
    public void cancelAll() {
        managed.forEach(EventSubscription::cancel);
        managed.clear();
    }

    public Plugin owner() {
        return owner;
    }

    /**
     * Fluent pipeline builder that mirrors {@link EventStream}'s API but registers
     * every subscription with the enclosing {@link PluginEventContext} and removes
     * completed subscriptions automatically via {@code doFinally}.
     */
    public final class BoundStream<E extends Event> {

        private final EventStream<E> inner;

        private BoundStream(EventStream<E> inner) {
            this.inner = inner;
        }

        public BoundStream<E> filter(Predicate<? super E> predicate) {
            return new BoundStream<>(inner.filter(predicate));
        }

        public BoundStream<E> ignoreCancelled() {
            return new BoundStream<>(inner.ignoreCancelled());
        }

        public BoundStream<E> debounce(Duration delay) {
            return new BoundStream<>(inner.debounce(delay));
        }

        public BoundStream<E> throttle(Duration period) {
            return new BoundStream<>(inner.throttle(period));
        }

        public BoundStream<E> limit(long count) {
            return new BoundStream<>(inner.limit(count));
        }

        public BoundStream<E> once() {
            return new BoundStream<>(inner.once());
        }

        public BoundStream<E> timeout(Duration timeout) {
            return new BoundStream<>(inner.timeout(timeout));
        }

        public <R extends Event> BoundStream<R> map(
                Function<? super E, ? extends R> mapper, Class<R> resultType) {
            return new BoundStream<>(inner.map(mapper, resultType));
        }

        public BoundStream<E> onMainThread() {
            return new BoundStream<>(inner.onMainThread());
        }

        public BoundStream<E> onAsync() {
            return new BoundStream<>(inner.onAsync());
        }

        public EventSubscription subscribe(Consumer<? super E> onEvent) {
            Objects.requireNonNull(onEvent, "onEvent must not be null");
            return trackWithRemoval(inner.flux(), onEvent,
                err -> owner.getSLF4JLogger().error("[NoblEvents] Unhandled error in subscriber for {}: {}",
                    inner.eventType().getSimpleName(), err.getMessage()),
                null);
        }

        public EventSubscription subscribe(Consumer<? super E> onEvent,
                                           Consumer<Throwable> onError) {
            Objects.requireNonNull(onEvent, "onEvent must not be null");
            Objects.requireNonNull(onError, "onError must not be null");
            return trackWithRemoval(inner.flux(), onEvent, onError, null);
        }

        public EventSubscription subscribe(Consumer<? super E> onEvent,
                                           Consumer<Throwable> onError,
                                           Runnable onComplete) {
            Objects.requireNonNull(onEvent, "onEvent must not be null");
            Objects.requireNonNull(onError, "onError must not be null");
            Objects.requireNonNull(onComplete, "onComplete must not be null");
            return trackWithRemoval(inner.flux(), onEvent, onError, onComplete);
        }

        /** Escape hatch: returns the underlying {@link Flux} without tracking. */
        public Flux<E> flux() {
            return inner.flux();
        }

        /**
         * Subscribes to the flux with a {@code doFinally} hook that removes the subscription
         * from {@code managed} once it terminates (complete, error, or cancel). The
         * {@code ref[]} indirection is safe because the hot multicast source never signals
         * synchronously during subscribe, so {@code ref[0]} is always set before doFinally fires.
         */
        private EventSubscription trackWithRemoval(
                Flux<E> flux,
                Consumer<? super E> onEvent,
                Consumer<Throwable> onError,
                Runnable onComplete) {

            final EventSubscription[] ref = new EventSubscription[1];

            final Flux<E> withRemoval = flux.doFinally(signal -> {
                if (ref[0] != null) managed.remove(ref[0]);
            });

            final Disposable d = onComplete != null
                ? withRemoval.subscribe(onEvent, onError, onComplete)
                : withRemoval.subscribe(onEvent, onError);

            final EventSubscription sub = new SimpleEventSubscription(d, inner.eventType());
            ref[0] = sub;
            managed.add(sub);
            return sub;
        }
    }
}
