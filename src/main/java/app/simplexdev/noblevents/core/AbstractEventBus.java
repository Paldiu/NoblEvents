package app.simplexdev.noblevents.core;

import app.simplexdev.noblevents.api.EventBus;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractEventBus implements EventBus {
    private static final long WARN_THROTTLE_MS = 5_000L;

    protected final Plugin plugin;

    private final ConcurrentHashMap<SinkKey, Sinks.Many<?>> sinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SinkKey, Listener> handles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SinkKey, AtomicInteger> subCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SinkKey, Long> lastWarnedAt = new ConcurrentHashMap<>();
    // Serializes (create + increment) against (decrement + teardown) so a new subscription can never
    // race the teardown of the last departing one for the same key.
    private final Object registrationLock = new Object();

    protected AbstractEventBus(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public <E extends Event> Flux<E> of(Class<E> eventType) {
        return of(eventType, EventPriority.NORMAL, false);
    }

    @Override
    public <E extends Event> Flux<E> of(Class<E> eventType, EventPriority priority, boolean ignoreCancelled) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(priority, "priority must not be null");

        final SinkKey key = new SinkKey(eventType, priority, ignoreCancelled);

        // Registration and the subscriber count are bound to the subscription lifecycle (via defer)
        // and guarded by registrationLock. This means an of(...) that is never subscribed registers
        // no Bukkit listener, and a fresh subscription can never observe a sink that the last
        // departing subscriber is concurrently tearing down.
        return Flux.defer(() -> acquire(key, eventType))
            .doFinally(signal -> release(key));
    }

    @SuppressWarnings("unchecked")
    private <E extends Event> Flux<E> acquire(final SinkKey key, final Class<E> eventType) {
        synchronized (registrationLock) {
            final Sinks.Many<E> sink = (Sinks.Many<E>) sinks.computeIfAbsent(
                key, k -> Sinks.many().multicast().onBackpressureBuffer(1024, false));
            handles.computeIfAbsent(key, k -> registerListener(key, eventType));
            subCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            return sink.asFlux();
        }
    }

    private void release(final SinkKey key) {
        synchronized (registrationLock) {
            final AtomicInteger count = subCounts.get(key);
            if (count == null) return;
            if (count.decrementAndGet() <= 0) teardown(key);
        }
    }

    private <E extends Event> Listener registerListener(final SinkKey key, final Class<E> eventType) {
        final Listener l = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(
            eventType,
            l,
            key.priority(),
            (listener, e) -> {
                if (eventType.isInstance(e)) emit(key, eventType.cast(e));
            },
            plugin,
            key.ignoreCancelled()
        );
        return l;
    }

    /**
     * Called on every matching Bukkit event before it reaches the sink.
     * Subclasses override this to apply interception, filtering, or transformation.
     */
    @SuppressWarnings("unchecked")
    protected <E extends Event> void emit(SinkKey key, E event) {
        final Sinks.Many<?> raw = sinks.get(key);
        if (raw == null) return;
        final Sinks.Many<E> s = (Sinks.Many<E>) raw;
        final Sinks.EmitResult result = s.tryEmitNext(event);
        if (result != Sinks.EmitResult.OK) {
            final long now = System.currentTimeMillis();
            final Long last = lastWarnedAt.get(key);
            if (last == null || now - last > WARN_THROTTLE_MS) {
                lastWarnedAt.put(key, now);
                plugin.getLogger().warning("[NoblEvents] emit dropped for "
                    + key.eventType().getSimpleName() + " (priority=" + key.priority()
                    + "): " + result);
            }
        }
    }

    private void teardown(SinkKey key) {
        subCounts.remove(key);
        lastWarnedAt.remove(key);
        final Sinks.Many<?> sink = sinks.remove(key);
        if (sink != null) sink.tryEmitComplete();
        final Listener l = handles.remove(key);
        if (l != null) HandlerList.unregisterAll(l);
    }

    public void shutdown() {
        sinks.values().forEach(Sinks.Many::tryEmitComplete);
        handles.values().forEach(HandlerList::unregisterAll);
        sinks.clear();
        handles.clear();
        subCounts.clear();
        lastWarnedAt.clear();
    }

    /**
     * Composite key that uniquely identifies a Bukkit event registration:
     * the event type, the listener priority, and whether already-cancelled events are ignored.
     */
    public record SinkKey(Class<? extends Event> eventType, EventPriority priority, boolean ignoreCancelled) {}
}
