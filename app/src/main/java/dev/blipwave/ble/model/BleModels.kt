package dev.blipwave.ble.model

import java.util.UUID

/** Tarama sırasında görülen tek bir reklam (advertisement) sonucunun özeti. */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMillis: Long,
    val isConnectable: Boolean = true,
    val serviceUuids: List<UUID> = emptyList(),
) {
    val hasName: Boolean get() = !name.isNullOrBlank()
}

/** Bağlantı yaşam döngüsü. UI'da Bağlanıyor / Bağlandı / Koptu olarak gösterilir. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState

    /**
     * @param unexpected kullanıcı istemeden kopma (menzil dışı, zaman aşımı, GATT hatası).
     */
    data class Disconnected(val unexpected: Boolean, val reason: String? = null) : ConnectionState
}

val ConnectionState.isActive: Boolean
    get() = this == ConnectionState.Connecting || this == ConnectionState.Connected

enum class CharacteristicProperty { READ, WRITE, WRITE_NO_RESPONSE, NOTIFY, INDICATE }

data class GattCharacteristicInfo(
    val uuid: UUID,
    val properties: Set<CharacteristicProperty>,
)

data class GattServiceInfo(
    val uuid: UUID,
    val characteristics: List<GattCharacteristicInfo>,
)

/** BLE katmanının UI'ya taşıdığı hata türleri. */
sealed class BleError(message: String) : Exception(message) {
    data object Unsupported : BleError("Bu cihazda BLE desteklenmiyor")
    data object BluetoothOff : BleError("Bluetooth kapalı")
    data object PermissionDenied : BleError("Bluetooth izni verilmedi")
    data class ScanFailed(val code: Int) : BleError("Tarama başlatılamadı (kod $code)")
    data object NotConnected : BleError("Cihaz bağlı değil")
    data object UartUnavailable : BleError("Cihazda Nordic UART servisi yok")
    data object WriteFailed : BleError("Yazma işlemi başarısız")
}
