package dev.blipwave.testing

import dev.blipwave.ble.BleBackend
import dev.blipwave.ble.BleBackendProvider
import dev.blipwave.ble.BleConnection
import dev.blipwave.ble.BleScanner
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.data.AppSettings
import dev.blipwave.data.SettingsRepository
import dev.blipwave.data.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update

/** Testlerin durumunu elle sürdüğü sahte bağlantı. */
class FakeConnection(
    override val address: String,
    override val deviceName: String? = null,
) : BleConnection {
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    override val rssi = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    override val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)

    val sent = mutableListOf<ByteArray>()
    var failSendsWith: BleError? = null
    var connectCalls = 0
    var closed = false

    override fun connect() {
        connectCalls++
        state.value = ConnectionState.Connected
    }

    override fun disconnect() {
        state.value = ConnectionState.Disconnected(unexpected = false)
    }

    override suspend fun send(data: ByteArray): Result<Unit> {
        failSendsWith?.let { return Result.failure(it) }
        sent += data
        return Result.success(Unit)
    }

    override fun close() {
        closed = true
        state.value = ConnectionState.Disconnected(unexpected = false)
    }
}

class FakeBackend(
    override val isDemo: Boolean = false,
    private val scanFlow: Flow<ScannedDevice> = emptyFlow(),
) : BleBackend {
    val created = mutableListOf<FakeConnection>()
    override val scanner = BleScanner { scanFlow }

    override fun createConnection(address: String, name: String?): BleConnection =
        FakeConnection(address, name).also { created += it }
}

class FakeBackendProvider(initial: BleBackend) : BleBackendProvider {
    override val backend = MutableStateFlow(initial)
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }
    override suspend fun setDemoMode(enabled: Boolean) = settings.update { it.copy(demoMode = enabled) }
}

fun device(
    address: String,
    name: String? = null,
    rssi: Int = -60,
    lastSeenMillis: Long = 0,
) = ScannedDevice(address = address, name = name, rssi = rssi, lastSeenMillis = lastSeenMillis)
