package dev.blipwave.ble.demo

import dev.blipwave.ble.BleConnection
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.ble.model.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

/**
 * Donanım olmadan gerçek bağlantıyı taklit eder: gecikmeli bağlanma, RSSI yürüyüşü,
 * echo cevapları ("PING" → "PONG") ve isteğe bağlı beklenmedik kopma.
 */
class DemoConnection(
    private val device: DemoDevice,
    private val parentScope: CoroutineScope,
    private val random: Random = Random.Default,
) : BleConnection {

    override val address: String = device.address
    override val deviceName: String? = device.name

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    override val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _rssi = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    override val rssi: SharedFlow<Int> = _rssi.asSharedFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    /** Tek bir bağlantı oturumunun tüm işleri; kopunca topluca iptal edilir. */
    private var linkScope: CoroutineScope? = null

    @Synchronized
    override fun connect() {
        if (_state.value.isActive) return
        _state.value = ConnectionState.Connecting
        _services.value = emptyList()

        val link = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext.job))
        linkScope = link
        link.launch {
            delay(CONNECT_DELAY_MILLIS)
            _services.value = device.services
            _state.value = ConnectionState.Connected

            launch { emitRssiLoop() }
            if (device.sendsTelemetry) launch { emitTelemetryLoop() }
            device.dropsAfterMillis?.let { dropAfter ->
                launch {
                    delay(dropAfter)
                    endLink(ConnectionState.Disconnected(unexpected = true, reason = "Link lost"))
                }
            }
        }
    }

    override fun disconnect() = endLink(ConnectionState.Disconnected(unexpected = false))

    override fun close() = endLink(ConnectionState.Disconnected(unexpected = false))

    override suspend fun send(data: ByteArray): Result<Unit> {
        val link = linkScope
        if (_state.value != ConnectionState.Connected || link == null) {
            return Result.failure(BleError.NotConnected)
        }
        if (!device.hasUart) return Result.failure(BleError.UartUnavailable)

        link.launch {
            delay(REPLY_DELAY_MILLIS + random.nextLong(0, 300))
            _incoming.emit(replyFor(data))
        }
        return Result.success(Unit)
    }

    @Synchronized
    private fun endLink(finalState: ConnectionState) {
        if (_state.value != ConnectionState.Idle) _state.value = finalState
        linkScope?.cancel()
        linkScope = null
    }

    private suspend fun emitRssiLoop() {
        var level = device.baseRssi
        while (true) {
            level = (level + random.nextInt(-3, 4)).coerceIn(device.baseRssi - 12, device.baseRssi + 8)
            _rssi.emit(level)
            delay(RSSI_INTERVAL_MILLIS)
        }
    }

    private suspend fun emitTelemetryLoop() {
        while (true) {
            delay(TELEMETRY_INTERVAL_MILLIS)
            val temperature = 21.0 + random.nextDouble(0.0, 3.0)
            _incoming.emit(String.format(Locale.US, "temp=%.1fC", temperature).toByteArray())
        }
    }

    companion object {
        const val CONNECT_DELAY_MILLIS = 1_400L
        const val REPLY_DELAY_MILLIS = 350L
        const val RSSI_INTERVAL_MILLIS = 1_000L
        const val TELEMETRY_INTERVAL_MILLIS = 7_000L

        /** ESP32 örnek firmware'i ile aynı davranış: PING → PONG, diğer her şey aynen geri döner. */
        fun replyFor(data: ByteArray): ByteArray {
            val text = data.toString(Charsets.UTF_8).trim()
            return if (text.equals("PING", ignoreCase = true)) "PONG".toByteArray() else data.copyOf()
        }
    }
}
