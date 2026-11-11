package dev.blipwave.ble.demo

import dev.blipwave.ble.BleScanner
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.data.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/** Sahte cihazları tek tek "keşfeder" ve RSSI değerlerini dalgalandırır. */
class DemoBleScanner(
    private val clock: Clock,
    private val random: Random = Random.Default,
    private val devices: List<DemoDevice> = DemoCatalog.devices,
    private val intervalMillis: Long = 450L,
) : BleScanner {

    override fun scan(): Flow<ScannedDevice> = flow {
        val levels = devices.associate { it.address to it.baseRssi }.toMutableMap()
        var visibleCount = 0
        while (true) {
            // Her turda bir cihaz daha görünür olur; kartlar sırayla belirir
            if (visibleCount < devices.size) visibleCount++
            for (device in devices.take(visibleCount)) {
                val next = (levels.getValue(device.address) + random.nextInt(-3, 4))
                    .coerceIn(device.baseRssi - 10, device.baseRssi + 8)
                levels[device.address] = next
                emit(
                    ScannedDevice(
                        address = device.address,
                        name = device.name,
                        rssi = next,
                        lastSeenMillis = clock.nowMillis(),
                        isConnectable = true,
                        serviceUuids = device.services.map { it.uuid },
                    ),
                )
            }
            delay(intervalMillis)
        }
    }
}
