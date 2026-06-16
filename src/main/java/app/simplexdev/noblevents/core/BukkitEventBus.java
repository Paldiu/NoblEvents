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

public class BukkitEventBus implements EventBus {

    private final Plugin plugin;
    private final ConcurrentHashMap<Class<?>, Sinks.Many<?>> sinks = new ConcurrentHashMap<>();
    private final Set<Class<?>> registered = ConcurrentHashMap.newKeySet();

    // Single dummy listener instance that holds all dynamic registrations.
    private final Listener handle = new Listener() {};

    public BukkitEventBus(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> Flux<E> of(Class<E> eventType) {
        Sinks.Many<E> sink = (Sinks.Many<E>) sinks.computeIfAbsent(
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
                        ((Sinks.Many<E>) sinks.get(eventType)).tryEmitNext(eventType.cast(e));
                    }
                },
                plugin,
                false
            );
        }

        return sink.asFlux();
    }

    @Override
    public void shutdown() {
        sinks.values().forEach(s -> s.tryEmitComplete());
        HandlerList.unregisterAll(handle);
        sinks.clear();
        registered.clear();
    }
}
