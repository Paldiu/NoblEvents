package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventBus;
import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.core.InterceptorAwareEventBus;
import app.simplexdev.noblevents.core.InterceptorChain;
import app.simplexdev.noblevents.impl.BukkitEventBus;
import app.simplexdev.noblevents.impl.interceptors.LoggingInterceptor;
import app.simplexdev.noblevents.scheduler.BukkitSchedulers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class NoblEvents extends JavaPlugin {

    private static NoblEvents instance;

    private InterceptorChain chain;
    private final ConcurrentHashMap<Plugin, PluginEventContext> contexts = new ConcurrentHashMap<>();
    private InterceptorAwareEventBus eventBus;

    @Override
    public void onEnable() {
        instance = this;
        chain = new InterceptorChain(getSLF4JLogger());
        chain.register(new LoggingInterceptor(getSLF4JLogger()));
        eventBus = new InterceptorAwareEventBus(new BukkitEventBus(this), chain);
        getServer().getPluginManager().registerEvents(new PluginLifecycleListener(), this);
        getSLF4JLogger().info("NoblEvents enabled.");
    }

    @Override
    public void onDisable() {
        contexts.values().forEach(PluginEventContext::cancelAll);
        contexts.clear();
        if (eventBus != null) {
            eventBus.shutdown();
            eventBus = null;
        }
        BukkitSchedulers.dispose(this);
        instance = null;
        getSLF4JLogger().info("NoblEvents disabled.");
    }

    /**
     * Returns a fluent {@link EventStream} for the given Bukkit event type at
     * {@link EventPriority#NORMAL} priority.
     *
     * <pre>{@code
     * NoblEvents.events(PlayerMoveEvent.class)
     *     .filter(e -> e.getPlayer().getHealth() < 5)
     *     .debounce(Duration.ofMillis(200))
     *     .onMainThread()
     *     .subscribe(e -> sendWarning(e.getPlayer()));
     * }</pre>
     *
     * @throws IllegalStateException if NoblEvents is not enabled
     */
    public static <E extends Event> EventStream<E> events(Class<E> eventType) {
        requireEnabled();
        return new EventStream<>(eventType, instance.eventBus.of(eventType), instance);
    }

    /**
     * Returns a fluent {@link EventStream} for the given event type at the specified
     * Bukkit listener {@code priority}. When {@code ignoreCancelled} is {@code true},
     * events already cancelled by higher-priority listeners are suppressed from the stream.
     *
     * @throws IllegalStateException if NoblEvents is not enabled
     */
    public static <E extends Event> EventStream<E> events(Class<E> eventType,
                                                          EventPriority priority,
                                                          boolean ignoreCancelled) {
        requireEnabled();
        return new EventStream<>(eventType,
            instance.eventBus.of(eventType, priority, ignoreCancelled), instance);
    }

    /**
     * Returns a {@link PluginEventContext} bound to the given plugin. All subscriptions
     * created through the context are automatically cancelled when that plugin disables.
     *
     * @throws IllegalStateException if NoblEvents is not enabled
     */
    public static PluginEventContext forPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        requireEnabled();
        return instance.contexts.computeIfAbsent(plugin, PluginEventContext::new);
    }

    /**
     * Registers a custom {@link EventInterceptor} into the global chain.
     * Interceptors run before every event reaches subscribers. Use
     * {@link app.simplexdev.noblevents.api.interceptor.Intercepts @Intercepts} to
     * restrict which event types it handles and to control ordering.
     *
     * <p><b>Trust boundary:</b> this is a public static method — any plugin can register
     * a global interceptor that sees and can cancel events for all other plugins.
     *
     * @throws IllegalStateException if NoblEvents is not enabled
     */
    public static void registerInterceptor(EventInterceptor interceptor) {
        requireEnabled();
        instance.chain.register(interceptor);
    }

    /**
     * @throws IllegalStateException if NoblEvents is not enabled
     */
    public static void unregisterInterceptor(EventInterceptor interceptor) {
        requireEnabled();
        instance.chain.unregister(interceptor);
    }

    /**
     * Returns the raw {@link EventBus} for callers that want to compose their own
     * pipelines without going through {@link EventStream}.
     *
     * @throws IllegalStateException if NoblEvents is not enabled
     */
    public static EventBus getEventBus() {
        requireEnabled();
        return instance.eventBus;
    }

    public static NoblEvents getInstance() {
        return instance;
    }

    static void requireEnabled() {
        if (instance == null || instance.eventBus == null) {
            throw new IllegalStateException("NoblEvents is not enabled");
        }
    }

    private final class PluginLifecycleListener implements Listener {
        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            final PluginEventContext ctx = contexts.remove(event.getPlugin());
            if (ctx != null) ctx.cancelAll();
        }
    }
}
