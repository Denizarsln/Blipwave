package dev.blipwave.data

import dev.blipwave.ble.BleBackendProvider
import dev.blipwave.ble.model.isActive
import dev.blipwave.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Açık cihaz oturumlarının tek sahibi. Detay, sohbet ve "Bağlantılar" ekranları
 * aynı [DeviceSession] nesnesini paylaşır.
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val backendProvider: BleBackendProvider,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val _sessions = MutableStateFlow<Map<String, DeviceSession>>(emptyMap())
    val sessions: StateFlow<Map<String, DeviceSession>> = _sessions.asStateFlow()

    fun session(address: String): DeviceSession? = _sessions.value[address]

    /** Oturum yoksa etkin backend ile oluşturur; bağlı değilse bağlantıyı başlatır. */
    @Synchronized
    fun connect(address: String, name: String?): DeviceSession {
        val session = _sessions.value[address] ?: run {
            val connection = backendProvider.backend.value.createConnection(address, name)
            DeviceSession(connection, scope, clock).also { _sessions.value += (address to it) }
        }
        if (!session.connection.state.value.isActive) session.connection.connect()
        return session
    }

    fun disconnect(address: String) {
        session(address)?.connection?.disconnect()
    }

    /** Bağlantıyı kapatır ve oturumu (mesaj dökümüyle birlikte) siler. */
    @Synchronized
    fun forget(address: String) {
        val session = _sessions.value[address] ?: return
        _sessions.value -= address
        session.close()
    }
}
