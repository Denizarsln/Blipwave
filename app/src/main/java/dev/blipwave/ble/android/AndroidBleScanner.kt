package dev.blipwave.ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.blipwave.ble.BleScanner
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.data.Clock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Sistem BLE tarayıcısını soğuk bir Flow olarak sarar. */
class AndroidBleScanner(
    private val context: Context,
    private val clock: Clock,
) : BleScanner {

    @SuppressLint("MissingPermission") // İzinler UI katmanında PermissionGate ile alınır
    override fun scan(): Flow<ScannedDevice> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw BleError.Unsupported
        if (!adapter.isEnabled) throw BleError.BluetoothOff
        val scanner = adapter.bluetoothLeScanner ?: throw BleError.BluetoothOff

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toScannedDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toScannedDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleError.ScanFailed(errorCode))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            throw BleError.PermissionDenied
        }

        awaitClose {
            // Bluetooth bu arada kapatılmış olabilir; durdurma hatası yutulur
            runCatching { scanner.stopScan(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toScannedDevice(): ScannedDevice {
        // scanRecord adı izin gerektirmez; device.name BLUETOOTH_CONNECT ister
        val name = scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
        return ScannedDevice(
            address = device.address,
            name = name?.takeIf { it.isNotBlank() },
            rssi = rssi,
            lastSeenMillis = clock.nowMillis(),
            isConnectable = isConnectable,
            serviceUuids = scanRecord?.serviceUuids?.map { it.uuid }.orEmpty(),
        )
    }
}
