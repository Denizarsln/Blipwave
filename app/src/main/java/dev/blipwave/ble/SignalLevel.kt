package dev.blipwave.ble

/** RSSI değerinin kullanıcıya gösterilen dört kademeli karşılığı. */
enum class SignalLevel(val bars: Int) {
    EXCELLENT(4), GOOD(3), FAIR(2), WEAK(1);

    companion object {
        fun fromRssi(rssi: Int): SignalLevel = when {
            rssi >= -60 -> EXCELLENT
            rssi >= -72 -> GOOD
            rssi >= -85 -> FAIR
            else -> WEAK
        }
    }
}
