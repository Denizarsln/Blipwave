package dev.blipwave.ble

import java.util.UUID

/** Nordic UART Service (NUS) UUID'leri. RX: telefon → cihaz, TX: cihaz → telefon (notify). */
object NordicUart {
    val SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX_CHARACTERISTIC: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val TX_CHARACTERISTIC: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
}

/** Bluetooth SIG standart UUID'leri ve okunabilir adları. */
object GattUuids {
    val CLIENT_CONFIG_DESCRIPTOR: UUID = uuid16(0x2902)

    fun uuid16(short: Int): UUID =
        UUID.fromString(String.format("0000%04X-0000-1000-8000-00805F9B34FB", short))

    private val knownServices: Map<UUID, String> = mapOf(
        uuid16(0x1800) to "Generic Access",
        uuid16(0x1801) to "Generic Attribute",
        uuid16(0x180A) to "Device Information",
        uuid16(0x180F) to "Battery Service",
        uuid16(0x180D) to "Heart Rate",
        uuid16(0x181A) to "Environmental Sensing",
        uuid16(0x1809) to "Health Thermometer",
        NordicUart.SERVICE to "Nordic UART Service",
    )

    private val knownCharacteristics: Map<UUID, String> = mapOf(
        uuid16(0x2A00) to "Device Name",
        uuid16(0x2A01) to "Appearance",
        uuid16(0x2A05) to "Service Changed",
        uuid16(0x2A19) to "Battery Level",
        uuid16(0x2A24) to "Model Number",
        uuid16(0x2A25) to "Serial Number",
        uuid16(0x2A26) to "Firmware Revision",
        uuid16(0x2A29) to "Manufacturer Name",
        uuid16(0x2A37) to "Heart Rate Measurement",
        uuid16(0x2A38) to "Body Sensor Location",
        uuid16(0x2A6E) to "Temperature",
        uuid16(0x2A6F) to "Humidity",
        NordicUart.RX_CHARACTERISTIC to "UART RX",
        NordicUart.TX_CHARACTERISTIC to "UART TX",
    )

    fun serviceName(uuid: UUID): String? = knownServices[uuid]
    fun characteristicName(uuid: UUID): String? = knownCharacteristics[uuid]

    /** SIG taban UUID'sindeki kısa formu ("0x180F") döndürür, değilse tam UUID'yi. */
    fun shortLabel(uuid: UUID): String {
        val text = uuid.toString().uppercase()
        return if (text.endsWith("-0000-1000-8000-00805F9B34FB") && text.startsWith("0000")) {
            "0x" + text.substring(4, 8)
        } else {
            text
        }
    }
}
