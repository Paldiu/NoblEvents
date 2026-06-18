package app.simplexdev.noblevents.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Reactor {@link Scheduler} that dispatches every task onto Bukkit's main server thread.
 *
 * <p>Each submitted task returns a {@link Disposable} that cancels the underlying
 * {@link BukkitTask}. Workers set a disposed flag so that tasks already queued but not yet
 * executed are skipped after the worker is cancelled.
 *
 * <p><b>Timed operators ({@code debounce}, {@code throttle}, {@code timeout}, etc.) must not
 * be chained onto this scheduler</b> — the main-thread scheduler does not implement
 * {@code schedule(task, delay, unit)}, and those operators require a time-capable scheduler
 * (Reactor's default {@code Schedulers.parallel()} is used automatically when no explicit
 * scheduler is provided).
 */
final class BukkitScheduler implements Scheduler {

    private final Plugin plugin;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Disposable schedule(Runnable task) {
        if (disposed.get()) return Disposables.disposed();
        final BukkitTask bt = plugin.getServer().getScheduler().runTask(plugin, task);
        return bt::cancel;
    }

    @Override
    public Worker createWorker() {
        return new BukkitWorker();
    }

    @Override
    public void dispose() {
        disposed.set(true);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    private final class BukkitWorker implements Worker {

        private final AtomicBoolean workerDisposed = new AtomicBoolean(false);

        @Override
        public Disposable schedule(Runnable task) {
            if (workerDisposed.get() || disposed.get()) return Disposables.disposed();
            final BukkitTask bt = plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!workerDisposed.get() && !disposed.get()) {
                    task.run();
                }
            });
            return bt::cancel;
        }

        @Override
        public void dispose() {
            workerDisposed.set(true);
        }

        @Override
        public boolean isDisposed() {
            return workerDisposed.get();
        }
    }
}
