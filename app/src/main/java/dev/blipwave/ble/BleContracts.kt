package dev.blipwave.ble

import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.ble.model.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Soğuk akış: toplandığı sürece tarama yapar, iptal edilince taramayı durdurur.
 * Hata durumunda [dev.blipwave.ble.model.BleError] fırlatır.
 */
fun interface BleScanner {
    fun scan(): Flow<ScannedDevice>
}

/** Tek bir cihaza GATT bağlantısı. Gerçek ve demo uygulamaları aynı sözleşmeyi paylaşır. */
interface BleConnection {
    val address: String
    val deviceName: String?
    val state: StateFlow<ConnectionState>
    val services: StateFlow<List<GattServiceInfo>>

    /** Bağlıyken yaklaşık saniyede bir RSSI örneği. */
    val rssi: SharedFlow<Int>

    /** Nordic UART TX karakteristiğinden gelen ham veri. */
    val incoming: SharedFlow<ByteArray>

    fun connect()
    fun disconnect()

    /** UART RX karakteristiğine yazar; MTU'ya göre parçalara bölmek uygulamanın işidir. */
    suspend fun send(data: ByteArray): Result<Unit>

    /** Kaynakları serbest bırakır; sonrasında nesne yeniden kullanılmaz. */
    fun close()
}

/** Tarayıcı ve bağlantı fabrikasını bir arada sunar (gerçek donanım veya demo). */
interface BleBackend {
    val isDemo: Boolean
    val scanner: BleScanner
    fun createConnection(address: String, name: String?): BleConnection
}

/** O anki ayara göre etkin backend'i verir. */
interface BleBackendProvider {
    val backend: StateFlow<BleBackend>
}
