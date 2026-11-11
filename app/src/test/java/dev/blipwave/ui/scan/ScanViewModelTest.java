package dev.blipwave.ui.scan;

import static dev.blipwave.testing.Fakes.device;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.blipwave.ble.ScanFilter;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.testing.FakeScheduler;
import dev.blipwave.testing.Fakes.FakeBackend;
import dev.blipwave.testing.Fakes.FakeBackendProvider;
import dev.blipwave.testing.Fakes.FakeSettingsRepository;

public class ScanViewModelTest {

    /** LiveData'nın ana iş parçacığı denetimini JVM testinde devre dışı bırakır. */
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final FakeScheduler time = new FakeScheduler();
    private final FakeBackend backend = new FakeBackend(false);
    private final FakeBackendProvider provider = new FakeBackendProvider(backend);
    private final FakeSettingsRepository settings = new FakeSettingsRepository();
    private ScanViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new ScanViewModel(provider, settings, time, time);
    }

    private ScanUiState state() {
        return viewModel.uiState().getValue();
    }

    /** Arayüz güncelleme sınırlamasının (160 ms) geçmesini bekler. */
    private void settle() {
        time.advanceBy(ScanViewModel.UI_REFRESH_MILLIS);
    }

    private static List<String> addresses(List<ScannedDevice> devices) {
        List<String> result = new ArrayList<>();
        for (ScannedDevice device : devices) result.add(device.address);
        return result;
    }

    @Test
    public void startingAScanCollectsDevicesSortedBySignal() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", "Far", -90, time.nowMillis()));
        backend.scanner.emit(device("BB", "Near", -40, time.nowMillis()));
        settle();

        assertTrue(state().scanning);
        assertEquals(Arrays.asList("BB", "AA"), addresses(state().devices));
        assertEquals(2, state().totalCount);
    }

    @Test
    public void updatesAreThrottledUntilTheRefreshInterval() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", null, -50, time.nowMillis()));

        assertEquals(0, state().totalCount);
        settle();
        assertEquals(1, state().totalCount);
    }

    @Test
    public void stoppingKeepsTheResultsButStopsTheScanner() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", null, -50, time.nowMillis()));
        settle();

        viewModel.stopScan();

        assertFalse(state().scanning);
        assertFalse(backend.scanner.isRunning());
        assertEquals(1, state().devices.size());
    }

    @Test
    public void toggleScanStartsAndStops() {
        viewModel.toggleScan();
        assertTrue(state().scanning);

        viewModel.toggleScan();
        assertFalse(state().scanning);
    }

    @Test
    public void filtersHideDevicesWithoutChangingTheTotalCount() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", "Lamp", -50, time.nowMillis()));
        backend.scanner.emit(device("BB", null, -55, time.nowMillis()));
        backend.scanner.emit(device("CC", "Weak", -95, time.nowMillis()));
        settle();

        viewModel.setNamedOnly(true);
        viewModel.setMinRssi(-80);

        assertEquals(Collections.singletonList("AA"), addresses(state().devices));
        assertEquals(3, state().totalCount);
    }

    @Test
    public void sortOrderCanBeChangedWhileScanning() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", "Zed", -40, time.nowMillis()));
        backend.scanner.emit(device("BB", "Amp", -80, time.nowMillis()));
        settle();

        viewModel.setSortOrder(ScanFilter.SortOrder.NAME);

        assertEquals(Arrays.asList("BB", "AA"), addresses(state().devices));
    }

    @Test
    public void devicesThatStopAdvertisingArePruned() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", null, -50, time.nowMillis()));
        settle();
        assertEquals(1, state().totalCount);

        time.advanceBy(12_000);

        assertEquals(0, state().totalCount);
    }

    @Test
    public void scannerErrorsSurfaceInTheStateAndEndTheScan() {
        viewModel.startScan();

        backend.scanner.fail(BleError.bluetoothOff());

        assertEquals(BleError.bluetoothOff(), state().error);
        assertFalse(state().scanning);

        viewModel.dismissError();
        assertNull(state().error);
    }

    @Test
    public void switchingBackendMidScanRestartsWithAnEmptyList() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", null, -50, time.nowMillis()));
        settle();

        FakeBackend demoBackend = new FakeBackend(true);
        provider.backend.set(demoBackend);

        assertTrue(state().demo);
        assertTrue(state().scanning);
        assertEquals(0, state().totalCount);
        assertFalse(backend.scanner.isRunning());
        assertTrue(demoBackend.scanner.isRunning());

        demoBackend.scanner.emit(device("DD", null, -50, time.nowMillis()));
        settle();
        assertEquals(Collections.singletonList("DD"), addresses(state().devices));
    }

    @Test
    public void transportFilterSeparatesClassicAndLeDevices() {
        viewModel.startScan();
        backend.scanner.emit(device("AA", "Tag", -50, time.nowMillis()));
        backend.scanner.emit(ScannedDevice.classic("BB", "Headset", -60, time.nowMillis(),
                ScannedDevice.Category.AUDIO_VIDEO));
        settle();

        viewModel.setTransportFilter(ScanFilter.TransportFilter.CLASSIC);
        assertEquals(Collections.singletonList("BB"), addresses(state().devices));

        viewModel.setTransportFilter(ScanFilter.TransportFilter.LE);
        assertEquals(Collections.singletonList("AA"), addresses(state().devices));
        assertEquals(2, state().totalCount);
    }

    @Test
    public void enableDemoModeWritesTheSetting() {
        viewModel.enableDemoMode();

        assertTrue(settings.settings().get().demoMode);
    }
}
