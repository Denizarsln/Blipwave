package dev.blipwave.util;

import android.os.Handler;
import android.os.Looper;

/** Tüm işleri ana iş parçacığında çalıştırır; böylece durum değişiklikleri tek iş parçacığında olur. */
public final class HandlerScheduler implements Scheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public Cancellable schedule(Runnable task, long delayMillis) {
        handler.postDelayed(task, delayMillis);
        return () -> handler.removeCallbacks(task);
    }

    @Override
    public Cancellable scheduleRepeating(Runnable task, long initialDelayMillis, long periodMillis) {
        final boolean[] cancelled = {false};
        Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (cancelled[0]) return;
                task.run();
                if (!cancelled[0]) handler.postDelayed(this, periodMillis);
            }
        };
        handler.postDelayed(loop, initialDelayMillis);
        return () -> {
            cancelled[0] = true;
            handler.removeCallbacks(loop);
        };
    }

    @Override
    public void post(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            handler.post(task);
        }
    }
}
