package dev.blipwave.data;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.Clock;
import dev.blipwave.util.ObservableValue;

/**
 * Bir bağlantının ekranlardan bağımsız yaşayan durumu: RSSI geçmişi, mesaj dökümü
 * ve bayt sayaçları. Ekran değişse de veri kaybolmaz.
 */
public final class DeviceSession {

    public interface SendResult {
        void onResult(@Nullable BleError error);
    }

    private static final int MAX_MESSAGES = 500;

    private final BleConnection connection;
    private final Clock clock;
    private final List<Cancellable> subscriptions = new ArrayList<>();
    private long nextId = 1;

    private final ObservableValue<List<RssiHistory.Sample>> rssiHistory =
            new ObservableValue<>(Collections.emptyList());
    private final ObservableValue<List<ChatMessage>> messages = new ObservableValue<>(Collections.emptyList());
    private final ObservableValue<ByteStats> byteStats = new ObservableValue<>(ByteStats.EMPTY);

    public DeviceSession(BleConnection connection, Clock clock) {
        this.connection = connection;
        this.clock = clock;

        subscriptions.add(connection.rssi().subscribe(value -> rssiHistory.set(
                RssiHistory.append(rssiHistory.get(), new RssiHistory.Sample(clock.nowMillis(), value)))));
        subscriptions.add(connection.incoming().subscribe(payload -> {
            append(ChatMessage.Direction.RX, payload);
            byteStats.set(byteStats.get().plusReceived(payload.length));
        }));
        subscriptions.add(connection.state().observe(state -> {
            // Yeni bir bağlantı denemesinde grafik sıfırdan başlar
            if (state.status == ConnectionState.Status.CONNECTING) rssiHistory.set(Collections.emptyList());
        }));
    }

    public BleConnection connection() { return connection; }
    public ObservableValue<List<RssiHistory.Sample>> rssiHistory() { return rssiHistory; }
    public ObservableValue<List<ChatMessage>> messages() { return messages; }
    public ObservableValue<ByteStats> byteStats() { return byteStats; }

    /** Yalnızca başarıyla yazılan mesajlar döküme ve sayaca eklenir. */
    public void send(byte[] payload, SendResult result) {
        if (payload.length == 0) {
            result.onResult(null);
            return;
        }
        byte[] copy = payload.clone();
        connection.send(copy, error -> {
            if (error == null) {
                append(ChatMessage.Direction.TX, copy);
                byteStats.set(byteStats.get().plusSent(copy.length));
            }
            result.onResult(error);
        });
    }

    public void clearMessages() {
        messages.set(Collections.emptyList());
        byteStats.set(ByteStats.EMPTY);
    }

    public void close() {
        for (Cancellable subscription : subscriptions) subscription.cancel();
        subscriptions.clear();
        connection.close();
    }

    private void append(ChatMessage.Direction direction, byte[] payload) {
        List<ChatMessage> updated = new ArrayList<>(messages.get());
        updated.add(new ChatMessage(nextId++, direction, payload, clock.nowMillis()));
        if (updated.size() > MAX_MESSAGES) updated = updated.subList(updated.size() - MAX_MESSAGES, updated.size());
        messages.set(new ArrayList<>(updated));
    }
}
