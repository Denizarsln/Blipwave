package dev.blipwave.ble.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.GattUuids;
import dev.blipwave.ble.NordicUart;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.EventStream;
import dev.blipwave.util.ObservableValue;
import dev.blipwave.util.Scheduler;

/**
 * Gerçek GATT bağlantısı. Akış: bağlan → MTU iste → servisleri keşfet →
 * (varsa) UART TX bildirimlerini aç → CONNECTED.
 * Sistem geri çağrıları binder iş parçacığından gelir; hepsi ana iş parçacığına aktarılır.
 */
@SuppressLint("MissingPermission") // İzinler arayüz katmanında PermissionGate ile alınır
public final class AndroidGattConnection implements BleConnection {

    private static final String TAG = "AndroidGattConnection";
    private static final int DEFAULT_MTU = 23;
    private static final int PREFERRED_MTU = 247;
    private static final long CONNECT_TIMEOUT_MILLIS = 15_000L;
    private static final long WRITE_TIMEOUT_MILLIS = 5_000L;
    private static final long RSSI_INTERVAL_MILLIS = 1_000L;

    private final Context context;
    private final String address;
    @Nullable private final String deviceName;
    private final Scheduler scheduler;

    private final ObservableValue<ConnectionState> state = new ObservableValue<>(ConnectionState.IDLE);
    private final ObservableValue<List<GattServiceInfo>> services = new ObservableValue<>(Collections.emptyList());
    private final EventStream<Integer> rssi = new EventStream<>();
    private final EventStream<byte[]> incoming = new EventStream<>();

    @Nullable private BluetoothGatt gatt;
    @Nullable private BluetoothGattCharacteristic uartRx;
    private int mtu = DEFAULT_MTU;
    private Cancellable rssiTask = Cancellable.NONE;
    private Cancellable connectTimeout = Cancellable.NONE;

    /** Sırada bekleyen yazma; GATT aynı anda tek işlem kabul eder. */
    @Nullable private PendingWrite pendingWrite;
    private final List<PendingWrite> writeQueue = new ArrayList<>();

    public AndroidGattConnection(Context context, String address, @Nullable String deviceName, Scheduler scheduler) {
        this.context = context;
        this.address = address;
        this.deviceName = deviceName;
        this.scheduler = scheduler;
    }

    @Override public String getAddress() { return address; }
    @Nullable @Override public String getDeviceName() { return deviceName; }
    @Override public ObservableValue<ConnectionState> state() { return state; }
    @Override public ObservableValue<List<GattServiceInfo>> services() { return services; }
    @Override public EventStream<Integer> rssi() { return rssi; }
    @Override public EventStream<byte[]> incoming() { return incoming; }

    @Override
    public void connect() {
        if (state.get().isActive()) return;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            state.set(ConnectionState.disconnected(true, "Bluetooth off"));
            return;
        }
        state.set(ConnectionState.CONNECTING);
        services.set(Collections.emptyList());
        try {
            // Süren klasik keşif radyoyu meşgul eder ve bağlantıyı ciddi yavaşlatır
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            BluetoothDevice device = adapter.getRemoteDevice(address);
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            state.set(ConnectionState.disconnected(true, "Permission denied"));
            return;
        } catch (IllegalArgumentException e) {
            state.set(ConnectionState.disconnected(true, "Invalid address"));
            return;
        }
        connectTimeout.cancel();
        connectTimeout = scheduler.schedule(() -> {
            if (state.get().status == ConnectionState.Status.CONNECTING) {
                Log.w(TAG, "Bağlantı zaman aşımına uğradı: " + address);
                teardown(ConnectionState.disconnected(true, "Timeout"));
            }
        }, CONNECT_TIMEOUT_MILLIS);
    }

    @Override
    public void disconnect() {
        teardown(ConnectionState.disconnectedByUser());
    }

    @Override
    public void close() {
        teardown(ConnectionState.disconnectedByUser());
    }

    @Override
    public void send(byte[] data, SendCallback callback) {
        if (!state.get().isConnected() || gatt == null) {
            callback.onComplete(BleError.notConnected());
            return;
        }
        if (uartRx == null) {
            callback.onComplete(BleError.uartUnavailable());
            return;
        }
        if (data.length == 0) {
            callback.onComplete(null);
            return;
        }
        writeQueue.add(new PendingWrite(data, callback));
        if (pendingWrite == null) startNextWrite();
    }

    // ---- Yazma kuyruğu -------------------------------------------------------

    private void startNextWrite() {
        if (writeQueue.isEmpty()) return;
        pendingWrite = writeQueue.remove(0);
        writeNextChunk();
    }

    private void writeNextChunk() {
        PendingWrite write = pendingWrite;
        if (write == null) return;
        if (write.offset >= write.data.length) {
            finishWrite(null);
            return;
        }
        // ATT başlığı 3 bayt; kalan MTU kadar parçalar halinde yaz
        int chunkSize = Math.max(1, mtu - 3);
        int end = Math.min(write.offset + chunkSize, write.data.length);
        byte[] chunk = Arrays.copyOfRange(write.data, write.offset, end);
        write.inFlight = chunk.length;
        if (gatt == null || uartRx == null || !startWrite(gatt, uartRx, chunk)) {
            finishWrite(BleError.writeFailed());
            return;
        }
        write.timeout = scheduler.schedule(() -> finishWrite(BleError.writeFailed()), WRITE_TIMEOUT_MILLIS);
    }

    private void onChunkWritten(boolean success) {
        PendingWrite write = pendingWrite;
        if (write == null) return;
        write.timeout.cancel();
        if (!success) {
            finishWrite(BleError.writeFailed());
            return;
        }
        write.offset += write.inFlight;
        writeNextChunk();
    }

    private void finishWrite(@Nullable BleError error) {
        PendingWrite write = pendingWrite;
        if (write == null) return;
        write.timeout.cancel();
        pendingWrite = null;
        write.callback.onComplete(error);
        startNextWrite();
    }

    private boolean startWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
        int writeType = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS;
            }
            characteristic.setWriteType(writeType);
            characteristic.setValue(value);
            return gatt.writeCharacteristic(characteristic);
        } catch (SecurityException e) {
            return false;
        }
    }

    // ---- Yaşam döngüsü -------------------------------------------------------

    private void teardown(ConnectionState finalState) {
        connectTimeout.cancel();
        rssiTask.cancel();
        uartRx = null;
        mtu = DEFAULT_MTU;

        // Bekleyen yazmalar artık tamamlanamaz
        List<PendingWrite> abandoned = new ArrayList<>(writeQueue);
        writeQueue.clear();
        if (pendingWrite != null) {
            pendingWrite.timeout.cancel();
            abandoned.add(0, pendingWrite);
            pendingWrite = null;
        }

        BluetoothGatt current = gatt;
        gatt = null;
        if (current != null) {
            try {
                current.disconnect();
                current.close();
            } catch (SecurityException ignored) {
                // İzin geri alınmış olabilir; kapatmaya devam et
            }
        }
        // Hiç bağlanmamış bir nesneyi kapatmak "koptu" olarak görünmemeli
        if (state.get().status != ConnectionState.Status.IDLE) state.set(finalState);

        for (PendingWrite write : abandoned) write.callback.onComplete(BleError.notConnected());
    }

    private void markConnected(BluetoothGatt source) {
        if (gatt != source) return;
        connectTimeout.cancel();
        state.set(ConnectionState.CONNECTED);
        rssiTask.cancel();
        rssiTask = scheduler.scheduleRepeating(() -> {
            try {
                if (gatt != null) gatt.readRemoteRssi();
            } catch (SecurityException ignored) {
                // RSSI okunamazsa grafik boş kalır
            }
        }, 0, RSSI_INTERVAL_MILLIS);
    }

    private void handleServicesDiscovered(BluetoothGatt source, int status) {
        if (gatt != source) return;
        if (status != BluetoothGatt.GATT_SUCCESS) {
            teardown(ConnectionState.disconnected(true, "Discovery failed (" + status + ")"));
            return;
        }
        List<GattServiceInfo> discovered = new ArrayList<>();
        for (BluetoothGattService service : source.getServices()) {
            List<GattServiceInfo.Characteristic> characteristics = new ArrayList<>();
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                characteristics.add(new GattServiceInfo.Characteristic(
                        characteristic.getUuid(), toProperties(characteristic.getProperties())));
            }
            discovered.add(new GattServiceInfo(service.getUuid(), characteristics));
        }
        services.set(discovered);

        BluetoothGattService uart = source.getService(NordicUart.SERVICE);
        uartRx = uart != null ? uart.getCharacteristic(NordicUart.RX_CHARACTERISTIC) : null;
        BluetoothGattCharacteristic tx = uart != null ? uart.getCharacteristic(NordicUart.TX_CHARACTERISTIC) : null;
        BluetoothGattDescriptor cccd = tx != null ? tx.getDescriptor(GattUuids.CLIENT_CONFIG_DESCRIPTOR) : null;
        if (tx == null || cccd == null || !enableNotifications(source, tx, cccd)) {
            markConnected(source);
        }
        // Bildirim açılırsa CONNECTED durumuna onDescriptorWrite içinde geçilir
    }

    private boolean enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                        BluetoothGattDescriptor cccd) {
        try {
            if (!gatt.setCharacteristicNotification(characteristic, true)) return false;
            byte[] value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS;
            }
            cccd.setValue(value);
            return gatt.writeDescriptor(cccd);
        } catch (SecurityException e) {
            return false;
        }
    }

    private static Set<GattServiceInfo.Property> toProperties(int flags) {
        Set<GattServiceInfo.Property> result = EnumSet.noneOf(GattServiceInfo.Property.class);
        if ((flags & BluetoothGattCharacteristic.PROPERTY_READ) != 0) result.add(GattServiceInfo.Property.READ);
        if ((flags & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) result.add(GattServiceInfo.Property.WRITE);
        if ((flags & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            result.add(GattServiceInfo.Property.WRITE_NO_RESPONSE);
        }
        if ((flags & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) result.add(GattServiceInfo.Property.NOTIFY);
        if ((flags & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) result.add(GattServiceInfo.Property.INDICATE);
        return result;
    }

    // ---- Sistem geri çağrıları (binder iş parçacığı) -------------------------

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
            scheduler.post(() -> {
                if (gatt != source) return;
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    // Bazı cihazlar MTU isteğine yanıt vermez; istek reddedilirse doğrudan keşfe geç
                    try {
                        if (!source.requestMtu(PREFERRED_MTU)) source.discoverServices();
                    } catch (SecurityException e) {
                        teardown(ConnectionState.disconnected(true, "Permission denied"));
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Bağlantı koptu: " + address + " (durum " + status + ")");
                    String reason = status == BluetoothGatt.GATT_SUCCESS ? null : "GATT " + status;
                    // Kullanıcı kaynaklı kopuş teardown() içinde zaten işlenir; buraya düşen beklenmediktir
                    teardown(ConnectionState.disconnected(true, reason));
                }
            });
        }

        @Override
        public void onMtuChanged(BluetoothGatt source, int newMtu, int status) {
            scheduler.post(() -> {
                if (gatt != source) return;
                if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu;
                try {
                    source.discoverServices();
                } catch (SecurityException e) {
                    teardown(ConnectionState.disconnected(true, "Permission denied"));
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt source, int status) {
            scheduler.post(() -> handleServicesDiscovered(source, status));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
            scheduler.post(() -> markConnected(source));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt source, BluetoothGattCharacteristic characteristic,
                                          int status) {
            boolean success = status == BluetoothGatt.GATT_SUCCESS;
            scheduler.post(() -> {
                if (gatt == source) onChunkWritten(success);
            });
        }

        // Android 13+
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt source,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            if (!NordicUart.TX_CHARACTERISTIC.equals(characteristic.getUuid())) return;
            byte[] copy = value.clone();
            scheduler.post(() -> incoming.emit(copy));
        }

        // Android 12 ve öncesi
        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return;
            if (!NordicUart.TX_CHARACTERISTIC.equals(characteristic.getUuid())) return;
            byte[] value = characteristic.getValue();
            if (value == null) return;
            byte[] copy = value.clone();
            scheduler.post(() -> incoming.emit(copy));
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt source, int value, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            scheduler.post(() -> rssi.emit(value));
        }
    };

    private static final class PendingWrite {
        final byte[] data;
        final SendCallback callback;
        int offset;
        int inFlight;
        Cancellable timeout = Cancellable.NONE;

        PendingWrite(byte[] data, SendCallback callback) {
            this.data = data;
            this.callback = callback;
        }
    }
}
