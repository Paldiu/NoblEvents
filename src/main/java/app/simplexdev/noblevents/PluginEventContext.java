package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventSubscription;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A plugin-scoped event subscription manager. Every subscription created through a
 * {@link BoundStream} returned by {@link #events} is tracked here and automatically
 * cancelled when the owning plugin disables (or when {@link #cancelAll()} is called
 * explicitly).
 *
 * <p>Obtain an instance via {@link NoblEvents#forPlugin(Plugin)}.
 *
 * <pre>{@code
 * // In your plugin's onEnable():
 * PluginEventContext ctx = NoblEvents.forPlugin(this);
 *
 * ctx.events(PlayerMoveEvent.class)
 *     .filter(e -> e.getPlayer().getHealth() < 5)
 *     .onMainThread()
 *     .subscribe(e -> sendWarning(e.getPlayer()));
 *
 * // All subscriptions are cancelled automatically when this plugin disables.
 * }</pre>
 */
public final class PluginEventContext {

    private final Plugin owner;
    private final List<EventSubscription> managed = new CopyOnWriteArrayList<>();

    PluginEventContext(Plugin owner) {
        this.owner = owner;
    }

    /**
     * Returns a fluent stream builder for the given event type. Subscriptions
     * created via the returned {@link BoundStream} are tracked by this context.
     */
    public <E extends Event> BoundStream<E> events(Class<E> eventType) {
        NoblEvents.requireEnabled();
        return new BoundStream<>(NoblEvents.events(eventType));
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

    private EventSubscription track(EventSubscription sub) {
        managed.add(sub);
        return sub;
    }

    // -------------------------------------------------------------------------

    /**
     * Fluent pipeline builder that mirrors {@link EventStream}'s API but registers
     * every subscription with the enclosing {@link PluginEventContext}.
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
            return track(inner.subscribe(onEvent));
        }

        public EventSubscription subscribe(Consumer<? super E> onEvent,
                                           Consumer<Throwable> onError) {
            return track(inner.subscribe(onEvent, onError));
        }

        public EventSubscription subscribe(Consumer<? super E> onEvent,
                                           Consumer<Throwable> onError,
                                           Runnable onComplete) {
            return track(inner.subscribe(onEvent, onError, onComplete));
        }

        /** Escape hatch: returns the underlying {@link Flux} without tracking. */
        public Flux<E> flux() {
            return inner.flux();
        }
    }
}
