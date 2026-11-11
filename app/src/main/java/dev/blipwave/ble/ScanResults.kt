package dev.blipwave.ble

import dev.blipwave.ble.model.ScannedDevice

enum class SortOrder { SIGNAL, NAME, RECENT }

data class ScanFilter(
    val namedOnly: Boolean = false,
    val minRssi: Int = MIN_RSSI_FLOOR,
    val sortOrder: SortOrder = SortOrder.SIGNAL,
) {
    companion object {
        const val MIN_RSSI_FLOOR = -100
        const val MAX_RSSI_CEILING = -30
    }
}

/** Tarama sonuçlarını birleştiren, eskiyenleri ayıklayan ve filtreleyen saf fonksiyonlar. */
object ScanResults {

    /** Bir cihaz 10 sn görünmezse listeden düşer. */
    const val STALE_AFTER_MILLIS = 10_000L

    /**
     * Yeni reklamı mevcut haritaya işler. Yeni pakette isim yoksa eski ismi korur
     * (bazı cihazlar ismi yalnızca scan response içinde gönderir).
     */
    fun merge(current: Map<String, ScannedDevice>, update: ScannedDevice): Map<String, ScannedDevice> {
        val previous = current[update.address]
        val merged = if (previous != null && !update.hasName && previous.hasName) {
            update.copy(name = previous.name)
        } else {
            update
        }
        return current + (update.address to merged)
    }

    fun pruneStale(
        current: Map<String, ScannedDevice>,
        nowMillis: Long,
        staleAfterMillis: Long = STALE_AFTER_MILLIS,
    ): Map<String, ScannedDevice> =
        current.filterValues { nowMillis - it.lastSeenMillis <= staleAfterMillis }

    fun apply(devices: Collection<ScannedDevice>, filter: ScanFilter): List<ScannedDevice> {
        val filtered = devices.asSequence()
            .filter { !filter.namedOnly || it.hasName }
            .filter { it.rssi >= filter.minRssi }
        val sorted = when (filter.sortOrder) {
            SortOrder.SIGNAL -> filtered.sortedWith(compareByDescending<ScannedDevice> { it.rssi }.thenBy { it.address })
            SortOrder.NAME -> filtered.sortedWith(
                compareBy<ScannedDevice> { !it.hasName }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }
                    .thenBy { it.address },
            )
            SortOrder.RECENT -> filtered.sortedWith(compareByDescending<ScannedDevice> { it.lastSeenMillis }.thenBy { it.address })
        }
        return sorted.toList()
    }
}
