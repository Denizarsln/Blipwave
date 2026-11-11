package dev.blipwave.data;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dev.blipwave.ble.BleBackendProvider;
import dev.blipwave.ble.BleConnection;
import dev.blipwave.util.Clock;
import dev.blipwave.util.ObservableValue;

/**
 * Açık cihaz oturumlarının tek sahibi. Detay, sohbet ve "Bağlantılar" ekranları
 * aynı {@link DeviceSession} nesnesini paylaşır.
 */
@Singleton
public final class ConnectionManager {

    private final BleBackendProvider backendProvider;
    private final Clock clock;
    private final ObservableValue<Map<String, DeviceSession>> sessions =
            new ObservableValue<>(Collections.emptyMap());

    @Inject
    public ConnectionManager(BleBackendProvider backendProvider, Clock clock) {
        this.backendProvider = backendProvider;
        this.clock = clock;
    }

    public ObservableValue<Map<String, DeviceSession>> sessions() {
        return sessions;
    }

    @Nullable
    public DeviceSession session(String address) {
        return sessions.get().get(address);
    }

    /** BLE (GATT) bağlantısı; bkz. {@link #connect(String, String, boolean)}. */
    public DeviceSession connect(String address, @Nullable String name) {
        return connect(address, name, false);
    }

    /**
     * Oturum yoksa etkin backend ile oluşturur; bağlı değilse bağlantıyı başlatır.
     * @param classic klasik Bluetooth cihazı için seri port (SPP) bağlantısı kurulur
     */
    public DeviceSession connect(String address, @Nullable String name, boolean classic) {
        DeviceSession session = sessions.get().get(address);
        if (session == null) {
            BleConnection connection = backendProvider.backend().get().createConnection(address, name, classic);
            session = new DeviceSession(connection, clock);
            Map<String, DeviceSession> updated = new LinkedHashMap<>(sessions.get());
            updated.put(address, session);
            sessions.set(Collections.unmodifiableMap(updated));
        }
        if (!session.connection().state().get().isActive()) session.connection().connect();
        return session;
    }

    public void disconnect(String address) {
        DeviceSession session = session(address);
        if (session != null) session.connection().disconnect();
    }

    /** Bağlantıyı kapatır ve oturumu (mesaj dökümüyle birlikte) siler. */
    public void forget(String address) {
        DeviceSession session = session(address);
        if (session == null) return;
        Map<String, DeviceSession> updated = new LinkedHashMap<>(sessions.get());
        updated.remove(address);
        sessions.set(Collections.unmodifiableMap(updated));
        session.close();
    }
}
