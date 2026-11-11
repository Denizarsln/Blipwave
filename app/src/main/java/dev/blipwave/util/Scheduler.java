package dev.blipwave.util;

/**
 * Gecikmeli ve periyodik işler için soyutlama. Uygulamada ana iş parçacığında çalışır;
 * testlerde sanal zamanla ilerletilen sahte bir uygulama kullanılır.
 */
public interface Scheduler {

    Cancellable schedule(Runnable task, long delayMillis);

    Cancellable scheduleRepeating(Runnable task, long initialDelayMillis, long periodMillis);

    /** İşi mümkünse hemen, değilse ilk fırsatta ana iş parçacığında çalıştırır. */
    default void post(Runnable task) {
        schedule(task, 0);
    }
}
