package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventBus;
import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.core.InterceptorAwareEventBus;
import app.simplexdev.noblevents.core.InterceptorChain;
import app.simplexdev.noblevents.impl.BukkitEventBus;
import app.simplexdev.noblevents.impl.interceptors.LoggingInterceptor;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

public final class NoblEvents extends JavaPlugin {

    private static NoblEvents instance;

    private final InterceptorChain chain = new InterceptorChain();
    private EventBus eventBus;

    @Override
    public void onEnable() {
        instance = this;
        chain.register(new LoggingInterceptor(getLogger()));
        eventBus = new InterceptorAwareEventBus(new BukkitEventBus(this), chain);
        getLogger().info("NoblEvents enabled.");
    }

    @Override
    public void onDisable() {
        if (eventBus != null) {
            eventBus.shutdown();
        }
        getLogger().info("NoblEvents disabled.");
    }

    // -------------------------------------------------------------------------
    // Static API — primary user-facing surface
    // -------------------------------------------------------------------------

    /**
     * Returns a fluent {@link EventStream} for the given Bukkit event type.
     *
     * <pre>{@code
     * NoblEvents.events(PlayerMoveEvent.class)
     *     .filter(e -> e.getPlayer().getHealth() < 5)
     *     .debounce(Duration.ofMillis(200))
     *     .onMainThread()
     *     .subscribe(e -> sendWarning(e.getPlayer()));
     * }</pre>
     */
    public static <E extends Event> EventStream<E> events(Class<E> eventType) {
        return new EventStream<>(eventType, instance.eventBus.of(eventType), instance);
    }

    /**
     * Registers a custom {@link EventInterceptor} into the global chain.
     * Interceptors run before every event reaches subscribers. Use
     * {@link app.simplexdev.noblevents.api.interceptor.Intercepts @Intercepts} to
     * restrict which event types it handles and to control ordering.
     */
    public static void registerInterceptor(EventInterceptor interceptor) {
        instance.chain.register(interceptor);
    }

    public static void unregisterInterceptor(EventInterceptor interceptor) {
        instance.chain.unregister(interceptor);
    }

    // -------------------------------------------------------------------------
    // Advanced — direct bus access
    // -------------------------------------------------------------------------

    /**
     * Returns the raw {@link EventBus} for callers that want to compose their own
     * pipelines without going through {@link EventStream}.
     */
    public static EventBus getEventBus() {
        return instance.eventBus;
    }

    public static NoblEvents getInstance() {
        return instance;
    }
}
