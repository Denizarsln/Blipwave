package dev.blipwave.ble.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.GattUuids;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.util.EventStream;
import dev.blipwave.util.ObservableValue;
import dev.blipwave.util.Scheduler;

/**
 * Klasik Bluetooth seri port (SPP / RFCOMM) bağlantısı. BLE bağlantısıyla aynı sözleşmeyi
 * uygular; böylece UART konsolu klasik cihazlarla da çalışır.
 * Soket işlemleri bloklayıcı olduğu için ayrı iş parçacıklarında yürür, durum değişiklikleri
 * ana iş parçacığına aktarılır. Klasik bağlantıda RSSI okunamaz.
 */
@SuppressLint("MissingPermission") // İzinler arayüz katmanında PermissionGate ile alınır
public final class ClassicSppConnection implements BleConnection {

    private static final String TAG = "ClassicSppConnection";
    private static final int READ_BUFFER_SIZE = 1024;

    /** Bağlantı kurulunca detay ekranında görünen tek "servis". */
    private static final List<GattServiceInfo> SPP_SERVICES = Collections.singletonList(
            new GattServiceInfo(GattUuids.SERIAL_PORT, Collections.emptyList()));

    private final Context context;
    private final String address;
    @Nullable private final String deviceName;
    private final Scheduler scheduler;

    private final ObservableValue<ConnectionState> state = new ObservableValue<>(ConnectionState.IDLE);
    private final ObservableValue<List<GattServiceInfo>> services = new ObservableValue<>(Collections.emptyList());
    private final EventStream<Integer> rssi = new EventStream<>();
    private final EventStream<byte[]> incoming = new EventStream<>();

    /** Bağlanma ve yazma işleri sırayla yürüsün diye tek iş parçacıklı kuyruk. */
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Nullable private volatile BluetoothSocket socket;
    @Nullable private volatile OutputStream output;
    /** Her bağlantı denemesinin kimliği; eski denemelerin geç gelen sonuçları yok sayılır. */
    private volatile int generation;

    public ClassicSppConnection(Context context, String address, @Nullable String deviceName, Scheduler scheduler) {
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
        int attempt = ++generation;
        io.execute(() -> openSocket(adapter, attempt));
    }

    /** İş parçacığında çalışır: önce güvenli, olmazsa güvensiz RFCOMM soketi dener. */
    private void openSocket(BluetoothAdapter adapter, int attempt) {
        BluetoothSocket opened = null;
        String failure = null;
        try {
            // Süren keşif RFCOMM bağlantısını başarısız kılar veya çok yavaşlatır
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            BluetoothDevice device = adapter.getRemoteDevice(address);
            opened = tryConnect(device.createRfcommSocketToServiceRecord(GattUuids.SERIAL_PORT));
            if (opened == null) {
                // Bazı modüller kimlik doğrulamasız (insecure) soketi kabul eder
                opened = tryConnect(device.createInsecureRfcommSocketToServiceRecord(GattUuids.SERIAL_PORT));
            }
            if (opened == null) failure = "SPP connection failed";
        } catch (SecurityException e) {
            failure = "Permission denied";
        } catch (IllegalArgumentException e) {
            failure = "Invalid address";
        } catch (IOException e) {
            failure = e.getMessage();
        }

        if (opened == null) {
            String reason = failure;
            scheduler.post(() -> {
                if (attempt == generation) state.set(ConnectionState.disconnected(true, reason));
            });
            return;
        }

        BluetoothSocket connected = opened;
        if (attempt != generation) {
            // Bu arada kullanıcı vazgeçti
            closeQuietly(connected);
            return;
        }
        try {
            output = connected.getOutputStream();
            InputStream input = connected.getInputStream();
            socket = connected;
            scheduler.post(() -> {
                if (attempt != generation) return;
                services.set(SPP_SERVICES);
                state.set(ConnectionState.CONNECTED);
            });
            readLoop(input, attempt);
        } catch (IOException e) {
            closeQuietly(connected);
            scheduler.post(() -> {
                if (attempt == generation) state.set(ConnectionState.disconnected(true, e.getMessage()));
            });
        }
    }

    @Nullable
    private BluetoothSocket tryConnect(BluetoothSocket candidate) {
        try {
            candidate.connect();
            return candidate;
        } catch (IOException e) {
            Log.w(TAG, "RFCOMM bağlantısı başarısız: " + e.getMessage());
            closeQuietly(candidate);
            return null;
        }
    }

    /** Bağlantı açık kaldıkça gelen baytları okur; soket kapanınca biter. */
    private void readLoop(InputStream input, int attempt) {
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            try {
                while (true) {
                    int count = input.read(buffer);
                    if (count < 0) break;
                    if (count == 0) continue;
                    byte[] chunk = Arrays.copyOf(buffer, count);
                    scheduler.post(() -> incoming.emit(chunk));
                }
            } catch (IOException e) {
                Log.i(TAG, "Seri bağlantı kapandı: " + e.getMessage());
            }
            // Kullanıcı kapattıysa generation zaten artmıştır; buraya düşen beklenmedik kopuştur
            scheduler.post(() -> {
                if (attempt != generation) return;
                generation++;
                release();
                state.set(ConnectionState.disconnected(true, "Link lost"));
            });
        }, "spp-reader-" + address);
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public void disconnect() {
        generation++;
        release();
        if (state.get().status != ConnectionState.Status.IDLE) state.set(ConnectionState.disconnectedByUser());
    }

    @Override
    public void close() {
        disconnect();
        io.shutdown();
    }

    @Override
    public void send(byte[] data, SendCallback callback) {
        OutputStream stream = output;
        if (!state.get().isConnected() || stream == null) {
            callback.onComplete(BleError.notConnected());
            return;
        }
        if (data.length == 0) {
            callback.onComplete(null);
            return;
        }
        byte[] copy = data.clone();
        io.execute(() -> {
            BleError error = null;
            try {
                stream.write(copy);
                stream.flush();
            } catch (IOException e) {
                error = BleError.writeFailed();
            }
            BleError result = error;
            scheduler.post(() -> callback.onComplete(result));
        });
    }

    private void release() {
        BluetoothSocket current = socket;
        socket = null;
        output = null;
        if (current != null) closeQuietly(current);
    }

    private static void closeQuietly(BluetoothSocket target) {
        try {
            target.close();
        } catch (IOException ignored) {
            // Kapatma hatası önemsiz
        }
    }
}
