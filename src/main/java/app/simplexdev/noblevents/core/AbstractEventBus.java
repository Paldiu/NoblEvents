package app.simplexdev.noblevents.core;

import app.simplexdev.noblevents.api.EventBus;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractEventBus implements EventBus {

    protected final Plugin plugin;

    private final ConcurrentHashMap<Class<?>, Sinks.Many<?>> sinks = new ConcurrentHashMap<>();
    private final Set<Class<?>> registered = ConcurrentHashMap.newKeySet();
    private final Listener handle = new Listener() {};

    protected AbstractEventBus(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> Flux<E> of(Class<E> eventType) {
        final Sinks.Many<E> sink = (Sinks.Many<E>) sinks.computeIfAbsent(
            eventType,
            k -> Sinks.many().multicast().onBackpressureBuffer()
        );

        if (registered.add(eventType)) {
            plugin.getServer().getPluginManager().registerEvent(
                eventType,
                handle,
                EventPriority.MONITOR,
                (l, e) -> {
                    if (eventType.isInstance(e)) {
                        emit(eventType, eventType.cast(e));
                    }
                },
                plugin,
                false
            );
        }

        return sink.asFlux();
    }

    /**
     * Called on every matching Bukkit event before it reaches the sink.
     * Subclasses override this to apply interception, filtering, or transformation.
     * The default implementation emits directly.
     */
    @SuppressWarnings("unchecked")
    protected <E extends Event> void emit(Class<E> eventType, E event) {
        ((Sinks.Many<E>) sinks.get(eventType)).tryEmitNext(event);
    }

    @Override
    public void shutdown() {
        sinks.values().forEach(s -> s.tryEmitComplete());
        HandlerList.unregisterAll(handle);
        sinks.clear();
        registered.clear();
    }
}
