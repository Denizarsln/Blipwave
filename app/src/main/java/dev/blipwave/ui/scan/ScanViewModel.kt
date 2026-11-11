package dev.blipwave.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.blipwave.ble.BleBackendProvider
import dev.blipwave.ble.ScanFilter
import dev.blipwave.ble.ScanResults
import dev.blipwave.ble.SortOrder
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.data.Clock
import dev.blipwave.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val isScanning: Boolean = false,
    val isDemo: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
    val totalCount: Int = 0,
    val filter: ScanFilter = ScanFilter(),
    val error: BleError? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val backendProvider: BleBackendProvider,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val found = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    private val filter = MutableStateFlow(ScanFilter())
    private val scanning = MutableStateFlow(false)
    private val error = MutableStateFlow<BleError?>(null)
    private var scanJob: Job? = null

    val uiState: StateFlow<ScanUiState> = combine(
        // Yoğun ortamlarda saniyede yüzlerce reklam gelebilir. StateFlow ara değerleri atladığı için
        // her yayından sonra beklemek arayüzü en fazla ~6 Hz günceller
        found.transform { emit(it); delay(UI_REFRESH_MILLIS) },
        filter,
        scanning,
        error,
        backendProvider.backend,
    ) { devices, currentFilter, isScanning, currentError, backend ->
        ScanUiState(
            isScanning = isScanning,
            isDemo = backend.isDemo,
            devices = ScanResults.apply(devices.values, currentFilter),
            totalCount = devices.size,
            filter = currentFilter,
            error = currentError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

    init {
        // Demo modu değişince eski listedeki cihazlar artık geçerli değildir
        viewModelScope.launch {
            backendProvider.backend.drop(1).collect {
                found.value = emptyMap()
                error.value = null
            }
        }
    }

    fun toggleScan() {
        if (scanning.value) stopScan() else startScan()
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        error.value = null
        scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                // Tarama sürerken backend değişirse yeni backend ile baştan başla
                backendProvider.backend.collectLatest { backend ->
                    found.value = emptyMap()
                    coroutineScope {
                        val pruner = launch {
                            while (true) {
                                delay(PRUNE_INTERVAL_MILLIS)
                                found.update { ScanResults.pruneStale(it, clock.nowMillis()) }
                            }
                        }
                        try {
                            backend.scanner.scan().collect { device ->
                                found.update { ScanResults.merge(it, device) }
                            }
                        } finally {
                            pruner.cancel()
                        }
                    }
                }
            } catch (bleError: BleError) {
                error.value = bleError
            } finally {
                scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun setNamedOnly(enabled: Boolean) = filter.update { it.copy(namedOnly = enabled) }

    fun setMinRssi(minRssi: Int) = filter.update {
        it.copy(minRssi = minRssi.coerceIn(ScanFilter.MIN_RSSI_FLOOR, ScanFilter.MAX_RSSI_CEILING))
    }

    fun setSortOrder(order: SortOrder) = filter.update { it.copy(sortOrder = order) }

    fun enableDemoMode() {
        viewModelScope.launch { settingsRepository.setDemoMode(true) }
    }

    fun dismissError() {
        error.value = null
    }

    private companion object {
        const val PRUNE_INTERVAL_MILLIS = 1_000L
        const val UI_REFRESH_MILLIS = 160L
    }
}
