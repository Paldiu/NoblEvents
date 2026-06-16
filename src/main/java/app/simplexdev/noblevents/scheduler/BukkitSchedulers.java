package app.simplexdev.noblevents.scheduler;

import org.bukkit.plugin.Plugin;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public final class BukkitSchedulers {

    private BukkitSchedulers() {}

    /**
     * A {@link Scheduler} that dispatches work onto Bukkit's main server thread.
     * Safe to use with {@code publishOn} / {@code subscribeOn}.
     */
    public static Scheduler mainThread(Plugin plugin) {
        return Schedulers.fromExecutor(
            command -> plugin.getServer().getScheduler().runTask(plugin, command));
    }

    /**
     * A {@link Scheduler} backed by Bukkit's async thread pool.
     */
    public static Scheduler async(Plugin plugin) {
        return Schedulers.fromExecutor(
            command -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, command));
    }
}
