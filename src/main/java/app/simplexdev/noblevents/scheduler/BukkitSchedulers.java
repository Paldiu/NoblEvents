package app.simplexdev.noblevents.scheduler;

import org.bukkit.plugin.Plugin;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ConcurrentHashMap;

public final class BukkitSchedulers {

    private static final ConcurrentHashMap<Plugin, Scheduler> mainThreadCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Plugin, Scheduler> asyncCache = new ConcurrentHashMap<>();

    private BukkitSchedulers() {}

    /**
     * A {@link Scheduler} that dispatches work onto Bukkit's main server thread.
     * The scheduler is cached per plugin; repeated calls return the same instance.
     *
     * <p><b>Do not chain timed operators ({@code debounce}, {@code throttle}, etc.) onto
     * this scheduler.</b> Those operators require a time-capable scheduler and will default
     * to Reactor's {@code Schedulers.parallel()} automatically when none is specified.
     */
    public static Scheduler mainThread(Plugin plugin) {
        return mainThreadCache.computeIfAbsent(plugin, BukkitScheduler::new);
    }

    /**
     * A single-threaded {@link Scheduler} for async event processing. Tasks submitted to
     * this scheduler run on a dedicated thread (not Bukkit's shared async pool), which
     * satisfies Reactor's {@code publishOn} serialization requirement. Do not call
     * Bukkit API from handlers dispatched here.
     */
    public static Scheduler async(Plugin plugin) {
        return asyncCache.computeIfAbsent(plugin,
            p -> Schedulers.newSingle("noblevents-async-" + p.getName()));
    }

    /**
     * Disposes and removes cached schedulers for the given plugin.
     * Called by NoblEvents during onDisable to release resources.
     */
    public static void dispose(Plugin plugin) {
        final Scheduler main = mainThreadCache.remove(plugin);
        if (main != null) main.dispose();
        final Scheduler async = asyncCache.remove(plugin);
        if (async != null) async.dispose();
    }
}
