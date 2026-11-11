package dev.blipwave.ble.demo;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.EventStream;
import dev.blipwave.util.ObservableValue;
import dev.blipwave.util.Scheduler;

/**
 * Donanım olmadan gerçek bağlantıyı taklit eder: gecikmeli bağlanma, RSSI yürüyüşü,
 * echo cevapları ("PING" → "PONG") ve isteğe bağlı beklenmedik kopma.
 */
public final class DemoConnection implements BleConnection {

    public static final long CONNECT_DELAY_MILLIS = 1_400L;
    public static final long REPLY_DELAY_MILLIS = 350L;
    public static final long RSSI_INTERVAL_MILLIS = 1_000L;
    public static final long TELEMETRY_INTERVAL_MILLIS = 7_000L;

    private final DemoDevice device;
    private final Scheduler scheduler;
    private final Random random;

    private final ObservableValue<ConnectionState> state = new ObservableValue<>(ConnectionState.IDLE);
    private final ObservableValue<List<GattServiceInfo>> services = new ObservableValue<>(Collections.emptyList());
    private final EventStream<Integer> rssi = new EventStream<>();
    private final EventStream<byte[]> incoming = new EventStream<>();

    /** Tek bağlantı oturumunun tüm zamanlanmış işleri; kopunca topluca iptal edilir. */
    private final List<Cancellable> linkTasks = new ArrayList<>();
    private int rssiLevel;

    public DemoConnection(DemoDevice device, Scheduler scheduler, Random random) {
        this.device = device;
        this.scheduler = scheduler;
        this.random = random;
        this.rssiLevel = device.baseRssi;
    }

    @Override public String getAddress() { return device.address; }
    @Nullable @Override public String getDeviceName() { return device.name; }
    @Override public ObservableValue<ConnectionState> state() { return state; }
    @Override public ObservableValue<List<GattServiceInfo>> services() { return services; }
    @Override public EventStream<Integer> rssi() { return rssi; }
    @Override public EventStream<byte[]> incoming() { return incoming; }

    @Override
    public void connect() {
        if (state.get().isActive()) return;
        state.set(ConnectionState.CONNECTING);
        services.set(Collections.emptyList());
        linkTasks.add(scheduler.schedule(this::onLinkUp, CONNECT_DELAY_MILLIS));
    }

    private void onLinkUp() {
        services.set(device.services);
        state.set(ConnectionState.CONNECTED);
        rssiLevel = device.baseRssi;
        linkTasks.add(scheduler.scheduleRepeating(this::emitRssi, 0, RSSI_INTERVAL_MILLIS));
        if (device.sendsTelemetry) {
            linkTasks.add(scheduler.scheduleRepeating(this::emitTelemetry,
                    TELEMETRY_INTERVAL_MILLIS, TELEMETRY_INTERVAL_MILLIS));
        }
        if (device.dropsAfterMillis > 0) {
            linkTasks.add(scheduler.schedule(
                    () -> endLink(ConnectionState.disconnected(true, "Link lost")), device.dropsAfterMillis));
        }
    }

    @Override
    public void disconnect() {
        endLink(ConnectionState.disconnectedByUser());
    }

    @Override
    public void close() {
        endLink(ConnectionState.disconnectedByUser());
    }

    @Override
    public void send(byte[] data, SendCallback callback) {
        if (!state.get().isConnected()) {
            callback.onComplete(BleError.notConnected());
            return;
        }
        if (!device.hasUart()) {
            callback.onComplete(BleError.uartUnavailable());
            return;
        }
        byte[] reply = replyFor(data);
        linkTasks.add(scheduler.schedule(() -> incoming.emit(reply), REPLY_DELAY_MILLIS + random.nextInt(300)));
        callback.onComplete(null);
    }

    private void endLink(ConnectionState finalState) {
        for (Cancellable task : linkTasks) task.cancel();
        linkTasks.clear();
        if (state.get().status != ConnectionState.Status.IDLE) state.set(finalState);
    }

    private void emitRssi() {
        rssiLevel = Math.max(device.baseRssi - 12, Math.min(device.baseRssi + 8, rssiLevel + random.nextInt(7) - 3));
        rssi.emit(rssiLevel);
    }

    private void emitTelemetry() {
        double temperature = 21.0 + random.nextDouble() * 3.0;
        incoming.emit(String.format(Locale.US, "temp=%.1fC", temperature).getBytes(StandardCharsets.UTF_8));
    }

    /** ESP32 örnek firmware'i ile aynı davranış: PING → PONG, diğer her şey aynen geri döner. */
    public static byte[] replyFor(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8).trim();
        if (text.equalsIgnoreCase("PING")) return "PONG".getBytes(StandardCharsets.UTF_8);
        return data.clone();
    }
}
