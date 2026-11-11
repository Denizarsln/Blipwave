package dev.blipwave.ble.demo

import dev.blipwave.ble.BleBackend
import dev.blipwave.ble.BleConnection
import dev.blipwave.ble.BleScanner
import dev.blipwave.ble.GattUuids
import dev.blipwave.ble.model.CharacteristicProperty
import dev.blipwave.ble.model.GattCharacteristicInfo
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.data.Clock
import dev.blipwave.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoBleBackend @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    clock: Clock,
) : BleBackend {
    override val isDemo: Boolean = true
    override val scanner: BleScanner = DemoBleScanner(clock)

    override fun createConnection(address: String, name: String?): BleConnection {
        // Katalogda olmayan bir adres gelirse en sade profille bağlan
        val device = DemoCatalog.find(address) ?: DemoDevice(
            address = address,
            name = name,
            baseRssi = -70,
            services = listOf(
                GattServiceInfo(
                    GattUuids.uuid16(0x1800),
                    listOf(GattCharacteristicInfo(GattUuids.uuid16(0x2A00), setOf(CharacteristicProperty.READ))),
                ),
            ),
        )
        return DemoConnection(device, scope)
    }
}
