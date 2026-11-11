package dev.blipwave.ui.scan;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dev.blipwave.ble.BleBackend;
import dev.blipwave.ble.BleBackendProvider;
import dev.blipwave.ble.BleScanner;
import dev.blipwave.ble.ScanFilter;
import dev.blipwave.ble.ScanResults;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.data.SettingsRepository;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.Clock;
import dev.blipwave.util.Scheduler;

@HiltViewModel
public class ScanViewModel extends ViewModel {

    static final long PRUNE_INTERVAL_MILLIS = 1_000L;
    /** Yoğun ortamlarda saniyede yüzlerce reklam gelebilir; arayüzü en fazla ~6 Hz güncelle. */
    static final long UI_REFRESH_MILLIS = 160L;

    private final BleBackendProvider backendProvider;
    private final SettingsRepository settingsRepository;
    private final Clock clock;
    private final Scheduler scheduler;

    private final MutableLiveData<ScanUiState> uiState = new MutableLiveData<>();
    private final Cancellable backendSubscription;

    private Map<String, ScannedDevice> found = Collections.emptyMap();
    private ScanFilter filter = new ScanFilter();
    private boolean scanning;
    @Nullable private BleError error;
    private BleBackend currentBackend;

    private Cancellable scanHandle = Cancellable.NONE;
    private Cancellable pruner = Cancellable.NONE;
    private Cancellable pendingRefresh = Cancellable.NONE;
    private boolean refreshScheduled;

    @Inject
    public ScanViewModel(BleBackendProvider backendProvider, SettingsRepository settingsRepository,
                         Clock clock, Scheduler scheduler) {
        this.backendProvider = backendProvider;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
        this.scheduler = scheduler;
        this.currentBackend = backendProvider.backend().get();
        // Demo modu değişince eski listedeki cihazlar artık geçerli değildir
        this.backendSubscription = backendProvider.backend().observe(this::onBackendChanged);
        publish();
    }

    public LiveData<ScanUiState> uiState() {
        return uiState;
    }

    public void toggleScan() {
        if (scanning) stopScan(); else startScan();
    }

    public void startScan() {
        if (scanning) return;
        scanning = true;
        error = null;
        found = Collections.emptyMap();
        startScanner();
        pruner = scheduler.scheduleRepeating(() -> {
            Map<String, ScannedDevice> pruned = ScanResults.pruneStale(found, clock.nowMillis());
            if (pruned.size() != found.size()) {
                found = pruned;
                publish();
            }
        }, PRUNE_INTERVAL_MILLIS, PRUNE_INTERVAL_MILLIS);
        publish();
    }

    public void stopScan() {
        if (!scanning) return;
        scanning = false;
        scanHandle.cancel();
        scanHandle = Cancellable.NONE;
        pruner.cancel();
        publish();
    }

    public void setNamedOnly(boolean enabled) {
        filter = filter.withNamedOnly(enabled);
        publish();
    }

    public void setMinRssi(int minRssi) {
        filter = filter.withMinRssi(minRssi);
        publish();
    }

    public void setSortOrder(ScanFilter.SortOrder order) {
        filter = filter.withSortOrder(order);
        publish();
    }

    public void setTransportFilter(ScanFilter.TransportFilter transport) {
        filter = filter.withTransport(transport);
        publish();
    }

    public void enableDemoMode() {
        settingsRepository.setDemoMode(true);
    }

    public void dismissError() {
        error = null;
        publish();
    }

    private void startScanner() {
        scanHandle = currentBackend.scanner().start(new BleScanner.Listener() {
            @Override
            public void onDevice(ScannedDevice device) {
                found = ScanResults.merge(found, device);
                schedulePublish();
            }

            @Override
            public void onError(BleError scanError) {
                error = scanError;
                stopScan();
                publish();
            }
        });
    }

    private void onBackendChanged(BleBackend backend) {
        if (backend == currentBackend) return;
        currentBackend = backend;
        found = Collections.emptyMap();
        error = null;
        // Tarama sürerken backend değişirse yeni backend ile baştan başla
        if (scanning) {
            scanHandle.cancel();
            startScanner();
        }
        publish();
    }

    private void schedulePublish() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        pendingRefresh = scheduler.schedule(() -> {
            refreshScheduled = false;
            publish();
        }, UI_REFRESH_MILLIS);
    }

    private void publish() {
        uiState.setValue(new ScanUiState(
                scanning,
                currentBackend.isDemo(),
                ScanResults.apply(found.values(), filter),
                found.size(),
                filter,
                error));
    }

    @Override
    protected void onCleared() {
        backendSubscription.cancel();
        scanHandle.cancel();
        pruner.cancel();
        pendingRefresh.cancel();
    }
}
