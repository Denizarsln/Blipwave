package dev.blipwave.testing;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.blipwave.ble.BleBackend;
import dev.blipwave.ble.BleBackendProvider;
import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.BleScanner;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.data.AppSettings;
import dev.blipwave.data.SettingsRepository;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.EventStream;
import dev.blipwave.util.ObservableValue;

/** Testlerde kullanılan elle sürülen sahte nesneler. */
public final class Fakes {

    private Fakes() { }

    public static ScannedDevice device(String address, @Nullable String name, int rssi, long lastSeenMillis) {
        return new ScannedDevice(address, name, rssi, lastSeenMillis);
    }

    public static ScannedDevice device(String address) {
        return device(address, null, -60, 0);
    }

    /** Testin durumunu elle sürdüğü bağlantı. */
    public static final class FakeConnection implements BleConnection {
        private final String address;
        @Nullable private final String name;
        public final ObservableValue<ConnectionState> state = new ObservableValue<>(ConnectionState.IDLE);
        public final ObservableValue<List<GattServiceInfo>> services = new ObservableValue<>(Collections.emptyList());
        public final EventStream<Integer> rssi = new EventStream<>();
        public final EventStream<byte[]> incoming = new EventStream<>();
        public final List<byte[]> sent = new ArrayList<>();
        @Nullable public BleError failSendsWith;
        public int connectCalls;
        public boolean closed;

        public FakeConnection(String address, @Nullable String name) {
            this.address = address;
            this.name = name;
        }

        @Override public String getAddress() { return address; }
        @Nullable @Override public String getDeviceName() { return name; }
        @Override public ObservableValue<ConnectionState> state() { return state; }
        @Override public ObservableValue<List<GattServiceInfo>> services() { return services; }
        @Override public EventStream<Integer> rssi() { return rssi; }
        @Override public EventStream<byte[]> incoming() { return incoming; }

        @Override
        public void connect() {
            connectCalls++;
            state.set(ConnectionState.CONNECTED);
        }

        @Override
        public void disconnect() {
            state.set(ConnectionState.disconnectedByUser());
        }

        @Override
        public void send(byte[] data, SendCallback callback) {
            if (failSendsWith != null) {
                callback.onComplete(failSendsWith);
                return;
            }
            sent.add(data.clone());
            callback.onComplete(null);
        }

        @Override
        public void close() {
            closed = true;
            state.set(ConnectionState.disconnectedByUser());
        }
    }

    /** Tarama sonuçlarını testin elle ilettiği tarayıcı. */
    public static final class FakeScanner implements BleScanner {
        @Nullable private Listener listener;
        public int startCount;

        @Override
        public Cancellable start(Listener newListener) {
            startCount++;
            listener = newListener;
            return () -> listener = null;
        }

        public boolean isRunning() {
            return listener != null;
        }

        public void emit(ScannedDevice device) {
            if (listener != null) listener.onDevice(device);
        }

        public void fail(BleError error) {
            Listener current = listener;
            listener = null;
            if (current != null) current.onError(error);
        }
    }

    public static final class FakeBackend implements BleBackend {
        private final boolean demo;
        public final FakeScanner scanner = new FakeScanner();
        public final List<FakeConnection> created = new ArrayList<>();

        public FakeBackend(boolean demo) {
            this.demo = demo;
        }

        @Override public boolean isDemo() { return demo; }
        @Override public BleScanner scanner() { return scanner; }

        /** Her createConnection çağrısında istenen bağlantı türü (true = klasik SPP). */
        public final List<Boolean> classicFlags = new ArrayList<>();

        @Override
        public BleConnection createConnection(String address, @Nullable String name, boolean classic) {
            classicFlags.add(classic);
            FakeConnection connection = new FakeConnection(address, name);
            created.add(connection);
            return connection;
        }

        @Nullable
        public FakeConnection connectionFor(String address) {
            for (FakeConnection connection : created) {
                if (connection.getAddress().equals(address)) return connection;
            }
            return null;
        }
    }

    public static final class FakeBackendProvider implements BleBackendProvider {
        public final ObservableValue<BleBackend> backend;

        public FakeBackendProvider(BleBackend initial) {
            backend = new ObservableValue<>(initial);
        }

        @Override
        public ObservableValue<BleBackend> backend() {
            return backend;
        }
    }

    public static final class FakeSettingsRepository implements SettingsRepository {
        public final ObservableValue<AppSettings> settings = new ObservableValue<>(AppSettings.DEFAULT);

        @Override public ObservableValue<AppSettings> settings() { return settings; }

        @Override
        public void setThemeMode(AppSettings.ThemeMode mode) {
            settings.set(settings.get().withThemeMode(mode));
        }

        @Override
        public void setDemoMode(boolean enabled) {
            settings.set(settings.get().withDemoMode(enabled));
        }
    }
}
