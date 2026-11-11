package dev.blipwave.ble.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import dev.blipwave.ble.BleScanner;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.Clock;

/**
 * Klasik ("standart") Bluetooth keşfi. Bir keşif turu ~12 sn sürüp kendiliğinden biter;
 * tarama açık kaldıkça yeniden başlatılır. Hata olursa yalnızca loglanır, BLE taramasını etkilemez.
 */
@SuppressLint("MissingPermission") // İzinler arayüz katmanında PermissionGate ile alınır
final class ClassicDiscovery {

    private static final String TAG = "ClassicDiscovery";

    private ClassicDiscovery() { }

    static Cancellable start(Context context, BluetoothAdapter adapter, Clock clock, BleScanner.Listener listener) {
        final boolean[] stopped = {false};

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (stopped[0]) return;
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    ScannedDevice device = toScannedDevice(intent, clock);
                    if (device != null) listener.onDevice(device);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    // Tur bitti; tarama sürüyorsa bir sonrakini başlat
                    startRound(adapter);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // Bu yayınları Bluetooth sistem süreci gönderir; dışa açık kayıt gerekir
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        startRound(adapter);

        return () -> {
            if (stopped[0]) return;
            stopped[0] = true;
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // Zaten kaydı silinmiş
            }
            try {
                adapter.cancelDiscovery();
            } catch (SecurityException ignored) {
                // İzin geri alınmış olabilir
            }
        };
    }

    private static void startRound(BluetoothAdapter adapter) {
        try {
            if (!adapter.isDiscovering() && !adapter.startDiscovery()) {
                Log.w(TAG, "Klasik keşif başlatılamadı");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Klasik keşif için izin yok: " + e.getMessage());
        }
    }

    private static ScannedDevice toScannedDevice(Intent intent, Clock clock) {
        BluetoothDevice device = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                : legacyDevice(intent);
        if (device == null) return null;

        int type;
        try {
            type = device.getType();
        } catch (SecurityException e) {
            type = BluetoothDevice.DEVICE_TYPE_CLASSIC;
        }
        // startDiscovery BLE cihazlarını da bildirir; onlar zaten BLE taramasından geliyor
        if (type == BluetoothDevice.DEVICE_TYPE_LE) return null;

        String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
        if (name == null) {
            try {
                name = device.getName();
            } catch (SecurityException ignored) {
                name = null;
            }
        }
        if (name != null && name.trim().isEmpty()) name = null;

        short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
        BluetoothClass bluetoothClass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass.class)
                : legacyClass(intent);

        return ScannedDevice.classic(
                device.getAddress(),
                name,
                rssi == Short.MIN_VALUE ? -100 : rssi,
                clock.nowMillis(),
                categoryOf(bluetoothClass));
    }

    @SuppressWarnings("deprecation")
    private static BluetoothDevice legacyDevice(Intent intent) {
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    @SuppressWarnings("deprecation")
    private static BluetoothClass legacyClass(Intent intent) {
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
    }

    static ScannedDevice.Category categoryOf(BluetoothClass bluetoothClass) {
        if (bluetoothClass == null) return ScannedDevice.Category.UNKNOWN;
        switch (bluetoothClass.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return ScannedDevice.Category.AUDIO_VIDEO;
            case BluetoothClass.Device.Major.PHONE: return ScannedDevice.Category.PHONE;
            case BluetoothClass.Device.Major.COMPUTER: return ScannedDevice.Category.COMPUTER;
            case BluetoothClass.Device.Major.WEARABLE: return ScannedDevice.Category.WEARABLE;
            case BluetoothClass.Device.Major.PERIPHERAL: return ScannedDevice.Category.PERIPHERAL;
            case BluetoothClass.Device.Major.HEALTH: return ScannedDevice.Category.HEALTH;
            case BluetoothClass.Device.Major.IMAGING: return ScannedDevice.Category.IMAGING;
            case BluetoothClass.Device.Major.NETWORKING: return ScannedDevice.Category.NETWORKING;
            case BluetoothClass.Device.Major.TOY: return ScannedDevice.Category.TOY;
            default: return ScannedDevice.Category.UNKNOWN;
        }
    }
}
