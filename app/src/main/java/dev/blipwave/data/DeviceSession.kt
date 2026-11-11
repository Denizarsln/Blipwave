package dev.blipwave.data

import dev.blipwave.ble.BleConnection
import dev.blipwave.ble.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Bir bağlantının ekranlardan bağımsız yaşayan durumu: RSSI geçmişi, mesaj dökümü
 * ve bayt sayaçları. Ekran değişse de veri kaybolmaz.
 */
class DeviceSession(
    val connection: BleConnection,
    parentScope: CoroutineScope,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext.job))
    private val nextId = AtomicLong(1)

    private val _rssiHistory = MutableStateFlow<List<RssiSample>>(emptyList())
    val rssiHistory: StateFlow<List<RssiSample>> = _rssiHistory.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _byteStats = MutableStateFlow(ByteStats())
    val byteStats: StateFlow<ByteStats> = _byteStats.asStateFlow()

    init {
        scope.launch {
            connection.rssi.collect { value ->
                _rssiHistory.update { RssiHistory.append(it, RssiSample(clock.nowMillis(), value)) }
            }
        }
        scope.launch {
            connection.incoming.collect { payload ->
                append(MessageDirection.RX, payload)
                _byteStats.update { it.copy(receivedBytes = it.receivedBytes + payload.size) }
            }
        }
        scope.launch {
            // Yeni bir bağlantı denemesinde grafik sıfırdan başlar
            connection.state.collect { state ->
                if (state == ConnectionState.Connecting) _rssiHistory.value = emptyList()
            }
        }
    }

    /** Yalnızca başarıyla yazılan mesajlar döküme ve sayaca eklenir. */
    suspend fun send(payload: ByteArray): Result<Unit> {
        if (payload.isEmpty()) return Result.success(Unit)
        return connection.send(payload).onSuccess {
            append(MessageDirection.TX, payload)
            _byteStats.update { it.copy(sentBytes = it.sentBytes + payload.size) }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _byteStats.value = ByteStats()
    }

    fun close() {
        scope.cancel()
        connection.close()
    }

    private fun append(direction: MessageDirection, payload: ByteArray) {
        val message = ChatMessage(nextId.getAndIncrement(), direction, payload, clock.nowMillis())
        _messages.update { (it + message).takeLast(MAX_MESSAGES) }
    }

    private companion object {
        const val MAX_MESSAGES = 500
    }
}
