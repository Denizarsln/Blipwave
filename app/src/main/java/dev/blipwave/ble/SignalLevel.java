package dev.blipwave.ble;

/** RSSI değerinin kullanıcıya gösterilen dört kademeli karşılığı. */
public enum SignalLevel {
    EXCELLENT(4), GOOD(3), FAIR(2), WEAK(1);

    public final int bars;

    SignalLevel(int bars) {
        this.bars = bars;
    }

    public static SignalLevel fromRssi(int rssi) {
        if (rssi >= -60) return EXCELLENT;
        if (rssi >= -72) return GOOD;
        if (rssi >= -85) return FAIR;
        return WEAK;
    }
}
