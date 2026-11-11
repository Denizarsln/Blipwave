package dev.blipwave.ble.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.blipwave.ble.BleScanner;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.Clock;
import dev.blipwave.util.Scheduler;

/** Sistem BLE tarayıcısını saran uygulama. Sonuçlar ana iş parçacığında iletilir. */
public final class AndroidBleScanner implements BleScanner {

    private final Context context;
    private final Clock clock;
    private final Scheduler scheduler;

    public AndroidBleScanner(Context context, Clock clock, Scheduler scheduler) {
        this.context = context;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    @SuppressLint("MissingPermission") // İzinler arayüz katmanında PermissionGate ile alınır
    @Override
    public Cancellable start(Listener listener) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null) return fail(listener, BleError.unsupported());
        if (!adapter.isEnabled()) return fail(listener, BleError.bluetoothOff());
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return fail(listener, BleError.bluetoothOff());

        final boolean[] stopped = {false};
        final Cancellable[] classicHandle = {Cancellable.NONE};
        ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                ScannedDevice device = toScannedDevice(result);
                scheduler.post(() -> {
                    if (!stopped[0]) listener.onDevice(device);
                });
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) onScanResult(0, result);
            }

            @Override
            public void onScanFailed(int errorCode) {
                scheduler.post(() -> {
                    if (stopped[0]) return;
                    stopped[0] = true;
                    classicHandle[0].cancel();
                    listener.onError(BleError.scanFailed(errorCode));
                });
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        try {
            scanner.startScan(null, settings, callback);
        } catch (SecurityException e) {
            return fail(listener, BleError.permissionDenied());
        }

        // Klasik keşif BLE taramasıyla paralel çalışır; sonuçlar aynı dinleyiciye gider
        classicHandle[0] = ClassicDiscovery.start(context, adapter, clock, new Listener() {
            @Override
            public void onDevice(ScannedDevice device) {
                if (!stopped[0]) listener.onDevice(device);
            }

            @Override
            public void onError(BleError error) {
                // Klasik keşif hatası BLE taramasını durdurmaz
            }
        });

        return () -> {
            if (stopped[0]) return;
            stopped[0] = true;
            classicHandle[0].cancel();
            try {
                scanner.stopScan(callback);
            } catch (RuntimeException ignored) {
                // Bluetooth bu arada kapatılmış olabilir; durdurma hatası yutulur
            }
        };
    }

    private Cancellable fail(Listener listener, BleError error) {
        scheduler.post(() -> listener.onError(error));
        return Cancellable.NONE;
    }

    @SuppressLint("MissingPermission")
    private ScannedDevice toScannedDevice(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        // scanRecord adı izin gerektirmez; device.getName() BLUETOOTH_CONNECT ister
        String name = record != null ? record.getDeviceName() : null;
        if (name == null) {
            try {
                name = result.getDevice().getName();
            } catch (SecurityException ignored) {
                name = null;
            }
        }
        if (name != null && name.trim().isEmpty()) name = null;

        List<UUID> uuids = new ArrayList<>();
        if (record != null && record.getServiceUuids() != null) {
            for (ParcelUuid parcelUuid : record.getServiceUuids()) uuids.add(parcelUuid.getUuid());
        }
        return new ScannedDevice(
                result.getDevice().getAddress(),
                name,
                result.getRssi(),
                clock.nowMillis(),
                result.isConnectable(),
                uuids);
    }
}
