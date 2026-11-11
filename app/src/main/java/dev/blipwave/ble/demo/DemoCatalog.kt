package dev.blipwave.ble.demo

import dev.blipwave.ble.GattUuids
import dev.blipwave.ble.NordicUart
import dev.blipwave.ble.model.CharacteristicProperty.INDICATE
import dev.blipwave.ble.model.CharacteristicProperty.NOTIFY
import dev.blipwave.ble.model.CharacteristicProperty.READ
import dev.blipwave.ble.model.CharacteristicProperty.WRITE
import dev.blipwave.ble.model.CharacteristicProperty.WRITE_NO_RESPONSE
import dev.blipwave.ble.model.GattCharacteristicInfo
import dev.blipwave.ble.model.GattServiceInfo

/** Demo modunda görünen sahte bir cihazın tarifi. */
data class DemoDevice(
    val address: String,
    val name: String?,
    val baseRssi: Int,
    val services: List<GattServiceInfo>,
    /** Belirli aralıklarla kendiliğinden telemetri mesajı gönderir. */
    val sendsTelemetry: Boolean = false,
    /** Null değilse bağlantı bu süre sonunda beklenmedik şekilde kopar. */
    val dropsAfterMillis: Long? = null,
) {
    val hasUart: Boolean get() = services.any { it.uuid == NordicUart.SERVICE }
}

object DemoCatalog {

    private val genericAccess = GattServiceInfo(
        GattUuids.uuid16(0x1800),
        listOf(
            GattCharacteristicInfo(GattUuids.uuid16(0x2A00), setOf(READ)),
            GattCharacteristicInfo(GattUuids.uuid16(0x2A01), setOf(READ)),
        ),
    )
    private val genericAttribute = GattServiceInfo(
        GattUuids.uuid16(0x1801),
        listOf(GattCharacteristicInfo(GattUuids.uuid16(0x2A05), setOf(INDICATE))),
    )
    private val deviceInfo = GattServiceInfo(
        GattUuids.uuid16(0x180A),
        listOf(
            GattCharacteristicInfo(GattUuids.uuid16(0x2A29), setOf(READ)),
            GattCharacteristicInfo(GattUuids.uuid16(0x2A24), setOf(READ)),
            GattCharacteristicInfo(GattUuids.uuid16(0x2A26), setOf(READ)),
        ),
    )
    private val battery = GattServiceInfo(
        GattUuids.uuid16(0x180F),
        listOf(GattCharacteristicInfo(GattUuids.uuid16(0x2A19), setOf(READ, NOTIFY))),
    )
    private val heartRate = GattServiceInfo(
        GattUuids.uuid16(0x180D),
        listOf(
            GattCharacteristicInfo(GattUuids.uuid16(0x2A37), setOf(NOTIFY)),
            GattCharacteristicInfo(GattUuids.uuid16(0x2A38), setOf(READ)),
        ),
    )
    private val environment = GattServiceInfo(
        GattUuids.uuid16(0x181A),
        listOf(
            GattCharacteristicInfo(GattUuids.uuid16(0x2A6E), setOf(READ, NOTIFY)),
            GattCharacteristicInfo(GattUuids.uuid16(0x2A6F), setOf(READ, NOTIFY)),
        ),
    )
    private val uart = GattServiceInfo(
        NordicUart.SERVICE,
        listOf(
            GattCharacteristicInfo(NordicUart.RX_CHARACTERISTIC, setOf(WRITE, WRITE_NO_RESPONSE)),
            GattCharacteristicInfo(NordicUart.TX_CHARACTERISTIC, setOf(NOTIFY)),
        ),
    )

    val devices: List<DemoDevice> = listOf(
        DemoDevice("D0:0D:1E:00:00:01", "Blipwave Echo", -47, listOf(genericAccess, genericAttribute, deviceInfo, battery, uart)),
        DemoDevice("D0:0D:1E:00:00:02", "ESP32 UART", -58, listOf(genericAccess, genericAttribute, uart)),
        DemoDevice("D0:0D:1E:00:00:03", "Pulse Band", -66, listOf(genericAccess, deviceInfo, battery, heartRate)),
        DemoDevice(
            "D0:0D:1E:00:00:04", "Thermo Node", -73,
            listOf(genericAccess, environment, uart),
            sendsTelemetry = true,
        ),
        DemoDevice("D0:0D:1E:00:00:05", null, -81, listOf(genericAccess, genericAttribute)),
        DemoDevice(
            "D0:0D:1E:00:00:06", "Flaky Beacon", -87,
            listOf(genericAccess, uart),
            dropsAfterMillis = 20_000L,
        ),
        DemoDevice("D0:0D:1E:00:00:07", null, -93, listOf(genericAccess)),
    )

    fun find(address: String): DemoDevice? = devices.firstOrNull { it.address == address }
}
