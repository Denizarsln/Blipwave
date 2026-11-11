package dev.blipwave.data;

import java.util.ArrayList;
import java.util.List;

/** Canlı grafik için kayan zaman penceresi. */
public final class RssiHistory {

    public static final long WINDOW_MILLIS = 60_000L;

    public static final class Sample {
        public final long timestampMillis;
        public final int rssi;

        public Sample(long timestampMillis, int rssi) {
            this.timestampMillis = timestampMillis;
            this.rssi = rssi;
        }
    }

    private RssiHistory() { }

    /** Girdiyi değiştirmeden pencere dışına düşenleri atıp yeni örneği ekler. */
    public static List<Sample> append(List<Sample> history, Sample sample, long windowMillis) {
        long cutoff = sample.timestampMillis - windowMillis;
        List<Sample> result = new ArrayList<>(history.size() + 1);
        for (Sample existing : history) {
            if (existing.timestampMillis >= cutoff) result.add(existing);
        }
        result.add(sample);
        return result;
    }

    public static List<Sample> append(List<Sample> history, Sample sample) {
        return append(history, sample, WINDOW_MILLIS);
    }
}
