package dev.blipwave.ui.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.blipwave.ble.NordicUart
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.data.ConnectionManager
import dev.blipwave.data.RssiSample
import dev.blipwave.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DeviceDetailUiState(
    val address: String,
    val name: String?,
    val connection: ConnectionState = ConnectionState.Connecting,
    val services: List<GattServiceInfo> = emptyList(),
    val rssiHistory: List<RssiSample> = emptyList(),
) {
    val hasUart: Boolean get() = services.any { it.uuid == NordicUart.SERVICE }
    val canOpenConsole: Boolean get() = connection == ConnectionState.Connected && hasUart
}

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val connectionManager: ConnectionManager,
) : ViewModel() {

    private val address: String = checkNotNull(savedStateHandle[Routes.ARG_ADDRESS])
    private val name: String? = savedStateHandle.get<String>(Routes.ARG_NAME)?.takeIf { it.isNotBlank() }

    // Ekran açılır açılmaz bağlantı başlar; oturum zaten varsa aynısı kullanılır
    private val session = connectionManager.connect(address, name)

    val uiState: StateFlow<DeviceDetailUiState> = combine(
        session.connection.state,
        session.connection.services,
        session.rssiHistory,
    ) { connection, services, history ->
        DeviceDetailUiState(
            address = address,
            name = session.connection.deviceName ?: name,
            connection = connection,
            services = services,
            rssiHistory = history,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DeviceDetailUiState(address, name, session.connection.state.value),
    )

    fun connect() {
        connectionManager.connect(address, name)
    }

    fun disconnect() {
        connectionManager.disconnect(address)
    }
}
