package dev.blipwave.ble

import dev.blipwave.testing.device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanResultsTest {

    @Test
    fun `merge adds new devices and replaces existing ones by address`() {
        var map = ScanResults.merge(emptyMap(), device("AA", rssi = -70))
        map = ScanResults.merge(map, device("BB", rssi = -50))
        map = ScanResults.merge(map, device("AA", rssi = -40, lastSeenMillis = 5))

        assertEquals(2, map.size)
        assertEquals(-40, map.getValue("AA").rssi)
        assertEquals(5L, map.getValue("AA").lastSeenMillis)
    }

    @Test
    fun `merge keeps a known name when a later advertisement has none`() {
        var map = ScanResults.merge(emptyMap(), device("AA", name = "Sensor"))
        map = ScanResults.merge(map, device("AA", name = null, rssi = -45))

        assertEquals("Sensor", map.getValue("AA").name)
        assertEquals(-45, map.getValue("AA").rssi)
    }

    @Test
    fun `merge lets a new name replace the old one`() {
        var map = ScanResults.merge(emptyMap(), device("AA", name = "Old"))
        map = ScanResults.merge(map, device("AA", name = "New"))

        assertEquals("New", map.getValue("AA").name)
    }

    @Test
    fun `pruneStale drops devices not seen within the window`() {
        val map = mapOf(
            "AA" to device("AA", lastSeenMillis = 0),
            "BB" to device("BB", lastSeenMillis = 9_000),
        )

        val pruned = ScanResults.pruneStale(map, nowMillis = 12_000, staleAfterMillis = 10_000)

        assertEquals(setOf("BB"), pruned.keys)
    }

    @Test
    fun `pruneStale keeps a device exactly at the boundary`() {
        val map = mapOf("AA" to device("AA", lastSeenMillis = 2_000))

        assertTrue(ScanResults.pruneStale(map, nowMillis = 12_000, staleAfterMillis = 10_000).containsKey("AA"))
    }

    @Test
    fun `namedOnly hides devices without a name`() {
        val devices = listOf(device("AA", name = "Lamp"), device("BB"), device("CC", name = " "))

        val result = ScanResults.apply(devices, ScanFilter(namedOnly = true))

        assertEquals(listOf("AA"), result.map { it.address })
    }

    @Test
    fun `minRssi keeps only devices at or above the threshold`() {
        val devices = listOf(device("AA", rssi = -50), device("BB", rssi = -80), device("CC", rssi = -81))

        val result = ScanResults.apply(devices, ScanFilter(minRssi = -80))

        assertEquals(listOf("AA", "BB"), result.map { it.address })
    }

    @Test
    fun `signal sort puts the strongest device first`() {
        val devices = listOf(device("AA", rssi = -90), device("BB", rssi = -40), device("CC", rssi = -65))

        val result = ScanResults.apply(devices, ScanFilter(sortOrder = SortOrder.SIGNAL))

        assertEquals(listOf("BB", "CC", "AA"), result.map { it.address })
    }

    @Test
    fun `name sort is case insensitive and puts unnamed devices last`() {
        val devices = listOf(device("AA"), device("BB", name = "zeta"), device("CC", name = "Alpha"))

        val result = ScanResults.apply(devices, ScanFilter(sortOrder = SortOrder.NAME))

        assertEquals(listOf("CC", "BB", "AA"), result.map { it.address })
    }

    @Test
    fun `recent sort puts the most recently seen device first`() {
        val devices = listOf(
            device("AA", lastSeenMillis = 100),
            device("BB", lastSeenMillis = 300),
            device("CC", lastSeenMillis = 200),
        )

        val result = ScanResults.apply(devices, ScanFilter(sortOrder = SortOrder.RECENT))

        assertEquals(listOf("BB", "CC", "AA"), result.map { it.address })
    }

    @Test
    fun `equal signal strength falls back to a stable address order`() {
        val devices = listOf(device("CC", rssi = -60), device("AA", rssi = -60), device("BB", rssi = -60))

        val result = ScanResults.apply(devices, ScanFilter(sortOrder = SortOrder.SIGNAL))

        assertEquals(listOf("AA", "BB", "CC"), result.map { it.address })
    }

    @Test
    fun `signal level boundaries`() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevel.fromRssi(-60))
        assertEquals(SignalLevel.GOOD, SignalLevel.fromRssi(-61))
        assertEquals(SignalLevel.GOOD, SignalLevel.fromRssi(-72))
        assertEquals(SignalLevel.FAIR, SignalLevel.fromRssi(-85))
        assertEquals(SignalLevel.WEAK, SignalLevel.fromRssi(-86))
    }
}
