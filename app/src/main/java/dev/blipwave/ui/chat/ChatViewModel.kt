package dev.blipwave.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.data.ByteStats
import dev.blipwave.data.ChatMessage
import dev.blipwave.data.ConnectionManager
import dev.blipwave.data.PayloadCodec
import dev.blipwave.data.PayloadFormat
import dev.blipwave.ui.navigation.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val address: String,
    val name: String?,
    val connection: ConnectionState = ConnectionState.Idle,
    val messages: List<ChatMessage> = emptyList(),
    val stats: ByteStats = ByteStats(),
    val format: PayloadFormat = PayloadFormat.TEXT,
)

enum class ChatEvent { SEND_FAILED, NOT_CONNECTED }

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    connectionManager: ConnectionManager,
) : ViewModel() {

    private val address: String = checkNotNull(savedStateHandle[Routes.ARG_ADDRESS])

    // Normalde detay ekranından gelinir ve oturum hazırdır; süreç yeniden başladıysa yeniden bağlan
    private val session = connectionManager.session(address) ?: connectionManager.connect(address, null)

    private val format = MutableStateFlow(PayloadFormat.TEXT)

    /** TextField gecikmesiz güncellensin diye Compose state olarak tutulur. */
    var input by mutableStateOf("")
        private set

    var inputError by mutableStateOf(false)
        private set

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events: Flow<ChatEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ChatUiState> = combine(
        session.connection.state,
        session.messages,
        session.byteStats,
        format,
    ) { connection, messages, stats, currentFormat ->
        ChatUiState(address, session.connection.deviceName, connection, messages, stats, currentFormat)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatUiState(address, session.connection.deviceName, session.connection.state.value),
    )

    fun onInputChange(value: String) {
        input = value
        inputError = false
    }

    fun setFormat(newFormat: PayloadFormat) {
        if (format.value == newFormat) return
        // Yazılmakta olan girdiyi yeni gösterime çevir; çevrilemiyorsa olduğu gibi bırak
        if (input.isNotEmpty()) {
            PayloadCodec.encode(input, format.value).onSuccess { bytes ->
                input = when (newFormat) {
                    PayloadFormat.HEX -> PayloadCodec.toHex(bytes)
                    PayloadFormat.TEXT -> bytes.toString(Charsets.UTF_8)
                }
            }
        }
        inputError = false
        format.value = newFormat
    }

    fun send() = sendRaw(input, clearInput = true)

    /** Hazır komut çipleri (ör. PING) her zaman metin olarak gönderilir. */
    fun sendQuick(text: String) {
        viewModelScope.launch { deliver(text.toByteArray(Charsets.UTF_8)) }
    }

    fun clearLog() = session.clearMessages()

    private fun sendRaw(text: String, clearInput: Boolean) {
        if (text.isEmpty()) return
        val payload = PayloadCodec.encode(text, format.value).getOrElse {
            inputError = true
            return
        }
        if (clearInput) input = ""
        viewModelScope.launch { deliver(payload) }
    }

    private suspend fun deliver(payload: ByteArray) {
        session.send(payload).onFailure { error ->
            _events.send(if (error is BleError.NotConnected) ChatEvent.NOT_CONNECTED else ChatEvent.SEND_FAILED)
        }
    }
}
