package dev.blipwave.testing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.blipwave.util.Cancellable;
import dev.blipwave.util.Clock;
import dev.blipwave.util.Scheduler;

/** Sanal zamanla çalışan zamanlayıcı; testler zamanı advanceBy ile ilerletir. */
public final class FakeScheduler implements Scheduler, Clock {

    private static final class Task {
        final long id;
        long dueMillis;
        final long periodMillis;
        final Runnable runnable;
        boolean cancelled;

        Task(long id, long dueMillis, long periodMillis, Runnable runnable) {
            this.id = id;
            this.dueMillis = dueMillis;
            this.periodMillis = periodMillis;
            this.runnable = runnable;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private long now;
    private long nextId;

    @Override
    public long nowMillis() {
        return now;
    }

    @Override
    public Cancellable schedule(Runnable task, long delayMillis) {
        return add(task, delayMillis, 0);
    }

    @Override
    public Cancellable scheduleRepeating(Runnable task, long initialDelayMillis, long periodMillis) {
        return add(task, initialDelayMillis, periodMillis);
    }

    @Override
    public void post(Runnable task) {
        task.run();
    }

    private Cancellable add(Runnable runnable, long delayMillis, long periodMillis) {
        Task task = new Task(nextId++, now + delayMillis, periodMillis, runnable);
        tasks.add(task);
        return () -> {
            task.cancelled = true;
            tasks.remove(task);
        };
    }

    /** Vadesi gelen işleri sırayla çalıştırır (süre 0 olan işler dahil). */
    public void runCurrent() {
        advanceBy(0);
    }

    public void advanceBy(long millis) {
        long target = now + millis;
        while (true) {
            Task next = null;
            for (Task task : tasks) {
                if (task.dueMillis <= target && (next == null || task.dueMillis < next.dueMillis
                        || (task.dueMillis == next.dueMillis && task.id < next.id))) {
                    next = task;
                }
            }
            if (next == null) break;
            now = Math.max(now, next.dueMillis);
            if (next.periodMillis > 0) {
                next.dueMillis += next.periodMillis;
            } else {
                tasks.remove(next);
            }
            next.runnable.run();
        }
        now = target;
    }

    public int pendingTaskCount() {
        tasks.sort(Comparator.comparingLong(task -> task.dueMillis));
        return tasks.size();
    }
}
