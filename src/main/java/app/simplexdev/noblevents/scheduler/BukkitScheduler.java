package app.simplexdev.noblevents.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Reactor {@link Scheduler} backed by Bukkit's task scheduler.
 *
 * <p>Unlike a {@code Schedulers.fromExecutor()} wrapper, each submitted task returns a
 * {@link Disposable} that cancels the underlying {@link BukkitTask}. Workers set a disposed
 * flag checked inside the task body so that tasks already queued but not yet executed are
 * skipped after the worker (i.e. the downstream subscription) is cancelled.
 */
final class BukkitScheduler implements Scheduler {

    private final Plugin plugin;
    private final boolean async;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    BukkitScheduler(Plugin plugin, boolean async) {
        this.plugin = plugin;
        this.async = async;
    }

    @Override
    public Disposable schedule(Runnable task) {
        if (disposed.get()) return Disposables.disposed();
        final BukkitTask bt = submit(task);
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

    private BukkitTask submit(Runnable command) {
        return async
            ? plugin.getServer().getScheduler().runTaskAsynchronously(plugin, command)
            : plugin.getServer().getScheduler().runTask(plugin, command);
    }

    private final class BukkitWorker implements Worker {

        private final AtomicBoolean workerDisposed = new AtomicBoolean(false);

        @Override
        public Disposable schedule(Runnable task) {
            if (workerDisposed.get() || disposed.get()) return Disposables.disposed();
            final BukkitTask bt = submit(() -> {
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
