package dev.blipwave.data;

import java.util.Objects;

public final class ByteStats {

    public static final ByteStats EMPTY = new ByteStats(0, 0);

    public final long sentBytes;
    public final long receivedBytes;

    public ByteStats(long sentBytes, long receivedBytes) {
        this.sentBytes = sentBytes;
        this.receivedBytes = receivedBytes;
    }

    public ByteStats plusSent(long bytes) {
        return new ByteStats(sentBytes + bytes, receivedBytes);
    }

    public ByteStats plusReceived(long bytes) {
        return new ByteStats(sentBytes, receivedBytes + bytes);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ByteStats)) return false;
        ByteStats that = (ByteStats) other;
        return sentBytes == that.sentBytes && receivedBytes == that.receivedBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sentBytes, receivedBytes);
    }

    @Override
    public String toString() {
        return "ByteStats{tx=" + sentBytes + ", rx=" + receivedBytes + "}";
    }
}
