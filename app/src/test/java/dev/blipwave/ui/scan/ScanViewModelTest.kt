package dev.blipwave.ui.scan

import dev.blipwave.ble.SortOrder
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.data.Clock
import dev.blipwave.testing.FakeBackend
import dev.blipwave.testing.FakeBackendProvider
import dev.blipwave.testing.FakeSettingsRepository
import dev.blipwave.testing.device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private val advertisements = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 64)
    private val provider = FakeBackendProvider(FakeBackend(scanFlow = advertisements))
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** ViewModel'i kurar ve uiState'i canlı tutacak bir toplayıcı başlatır. */
    private fun TestScope.newViewModel(): ScanViewModel {
        val viewModel = ScanViewModel(provider, settings, Clock { testScheduler.currentTime })
        backgroundScope.launch { viewModel.uiState.collect { } }
        settle()
        return viewModel
    }

    /** Arayüz güncelleme sınırlamasının (160 ms) geçmesini bekler. */
    private fun TestScope.settle() = advanceTimeBy(400)

    @Test
    fun `starting a scan collects devices sorted by signal`() = scope.runTest {
        val viewModel = newViewModel()

        viewModel.startScan()
        settle()
        advertisements.emit(device("AA", name = "Far", rssi = -90))
        advertisements.emit(device("BB", name = "Near", rssi = -40))
        settle()

        val state = viewModel.uiState.value
        assertTrue(state.isScanning)
        assertEquals(listOf("BB", "AA"), state.devices.map { it.address })
        assertEquals(2, state.totalCount)
        viewModel.stopScan()
    }

    @Test
    fun `stopping keeps the results but clears the scanning flag`() = scope.runTest {
        val viewModel = newViewModel()
        viewModel.startScan()
        settle()
        advertisements.emit(device("AA", lastSeenMillis = testScheduler.currentTime))
        settle()

        viewModel.stopScan()
        settle()

        assertFalse(viewModel.uiState.value.isScanning)
        assertEquals(1, viewModel.uiState.value.devices.size)
    }

    @Test
    fun `filters hide devices without changing the total count`() = scope.runTest {
        val viewModel = newViewModel()
        viewModel.startScan()
        settle()
        advertisements.emit(device("AA", name = "Lamp", rssi = -50))
        advertisements.emit(device("BB", name = null, rssi = -55))
        advertisements.emit(device("CC", name = "Weak", rssi = -95))
        settle()

        viewModel.setNamedOnly(true)
        viewModel.setMinRssi(-80)
        settle()

        val state = viewModel.uiState.value
        assertEquals(listOf("AA"), state.devices.map { it.address })
        assertEquals(3, state.totalCount)
        viewModel.stopScan()
    }

    @Test
    fun `sort order can be changed while scanning`() = scope.runTest {
        val viewModel = newViewModel()
        viewModel.startScan()
        settle()
        advertisements.emit(device("AA", name = "Zed", rssi = -40))
        advertisements.emit(device("BB", name = "Amp", rssi = -80))
        settle()

        viewModel.setSortOrder(SortOrder.NAME)
        settle()

        assertEquals(listOf("BB", "AA"), viewModel.uiState.value.devices.map { it.address })
        viewModel.stopScan()
    }

    @Test
    fun `min rssi is clamped to the supported range`() = scope.runTest {
        val viewModel = newViewModel()

        viewModel.setMinRssi(-500)
        settle()
        assertEquals(-100, viewModel.uiState.value.filter.minRssi)

        viewModel.setMinRssi(0)
        settle()
        assertEquals(-30, viewModel.uiState.value.filter.minRssi)
    }

    @Test
    fun `devices that stop advertising are pruned`() = scope.runTest {
        val viewModel = newViewModel()
        viewModel.startScan()
        settle()
        advertisements.emit(device("AA", lastSeenMillis = testScheduler.currentTime))
        settle()
        assertEquals(1, viewModel.uiState.value.totalCount)

        advanceTimeBy(12_000)

        assertEquals(0, viewModel.uiState.value.totalCount)
        viewModel.stopScan()
    }

    @Test
    fun `scanner errors surface in the state and end the scan`() = scope.runTest {
        provider.backend.value = FakeBackend(scanFlow = flow { throw BleError.BluetoothOff })
        val viewModel = newViewModel()

        viewModel.startScan()
        settle()

        assertEquals(BleError.BluetoothOff, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isScanning)

        viewModel.dismissError()
        settle()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `switching backend mid-scan restarts with an empty list`() = scope.runTest {
        val viewModel = newViewModel()
        viewModel.startScan()
        settle()
        advertisements.emit(device("AA", lastSeenMillis = testScheduler.currentTime))
        settle()

        val demoAdvertisements = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 8)
        provider.backend.value = FakeBackend(isDemo = true, scanFlow = demoAdvertisements)
        settle()

        assertTrue(viewModel.uiState.value.isDemo)
        assertTrue(viewModel.uiState.value.isScanning)
        assertEquals(0, viewModel.uiState.value.totalCount)

        demoAdvertisements.emit(device("DD", lastSeenMillis = testScheduler.currentTime))
        settle()
        assertEquals(listOf("DD"), viewModel.uiState.value.devices.map { it.address })
        viewModel.stopScan()
    }

    @Test
    fun `enableDemoMode writes the setting`() = scope.runTest {
        val viewModel = newViewModel()

        viewModel.enableDemoMode()
        settle()

        assertTrue(settings.settings.value.demoMode)
    }
}
