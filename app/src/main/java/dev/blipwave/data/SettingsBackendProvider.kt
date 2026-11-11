package dev.blipwave.data

import dev.blipwave.ble.BleBackend
import dev.blipwave.ble.BleBackendProvider
import dev.blipwave.ble.android.AndroidBleBackend
import dev.blipwave.ble.demo.DemoBleBackend
import dev.blipwave.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Demo modu ayarına göre gerçek veya sahte backend'i seçer. */
@Singleton
class SettingsBackendProvider @Inject constructor(
    settingsRepository: SettingsRepository,
    androidBackend: AndroidBleBackend,
    demoBackend: DemoBleBackend,
    @ApplicationScope scope: CoroutineScope,
) : BleBackendProvider {

    override val backend: StateFlow<BleBackend> = settingsRepository.settings
        .map { if (it.demoMode) demoBackend else androidBackend }
        .stateIn(scope, SharingStarted.Eagerly, androidBackend)
}
