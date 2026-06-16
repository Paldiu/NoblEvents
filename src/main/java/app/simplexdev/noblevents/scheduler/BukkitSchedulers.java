package app.simplexdev.noblevents.scheduler;

import org.bukkit.plugin.Plugin;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.ConcurrentHashMap;

public final class BukkitSchedulers {

    private static final ConcurrentHashMap<Plugin, Scheduler> mainThreadCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Plugin, Scheduler> asyncCache = new ConcurrentHashMap<>();

    private BukkitSchedulers() {}

    /**
     * A {@link Scheduler} that dispatches work onto Bukkit's main server thread.
     * The scheduler is cached per plugin; repeated calls return the same instance.
     * Each submitted task returns a {@link reactor.core.Disposable} that cancels the
     * underlying {@link org.bukkit.scheduler.BukkitTask} if it has not yet executed.
     */
    public static Scheduler mainThread(Plugin plugin) {
        return mainThreadCache.computeIfAbsent(plugin, p -> new BukkitScheduler(p, false));
    }

    /**
     * A {@link Scheduler} backed by Bukkit's async thread pool.
     * The scheduler is cached per plugin; repeated calls return the same instance.
     */
    public static Scheduler async(Plugin plugin) {
        return asyncCache.computeIfAbsent(plugin, p -> new BukkitScheduler(p, true));
    }

    /**
     * Disposes and removes cached schedulers for the given plugin.
     * Called by NoblEvents during onDisable to release Reactor resources.
     */
    public static void dispose(Plugin plugin) {
        final Scheduler main = mainThreadCache.remove(plugin);
        if (main != null) main.dispose();
        final Scheduler async = asyncCache.remove(plugin);
        if (async != null) async.dispose();
    }
}
