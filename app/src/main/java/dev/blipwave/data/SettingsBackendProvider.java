package dev.blipwave.data;

import javax.inject.Inject;
import javax.inject.Singleton;

import dev.blipwave.ble.BleBackend;
import dev.blipwave.ble.BleBackendProvider;
import dev.blipwave.ble.android.AndroidBleBackend;
import dev.blipwave.ble.demo.DemoBleBackend;
import dev.blipwave.util.ObservableValue;

/** Demo modu ayarına göre gerçek veya sahte backend'i seçer. */
@Singleton
public final class SettingsBackendProvider implements BleBackendProvider {

    private final ObservableValue<BleBackend> backend;

    @Inject
    public SettingsBackendProvider(SettingsRepository settingsRepository, AndroidBleBackend androidBackend,
                                   DemoBleBackend demoBackend) {
        backend = new ObservableValue<>(androidBackend);
        // Uygulama ömrü boyunca yaşayan tekil nesne; aboneliğin iptaline gerek yok
        settingsRepository.settings().observe(settings ->
                backend.set(settings.demoMode ? demoBackend : androidBackend));
    }

    @Override
    public ObservableValue<BleBackend> backend() {
        return backend;
    }
}
