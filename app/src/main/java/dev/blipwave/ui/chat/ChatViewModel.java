package dev.blipwave.ui.chat;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dev.blipwave.R;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.data.ByteStats;
import dev.blipwave.data.ChatMessage;
import dev.blipwave.data.ConnectionManager;
import dev.blipwave.data.DeviceSession;
import dev.blipwave.data.PayloadCodec;
import dev.blipwave.util.Event;
import dev.blipwave.util.LiveDataBridge;

@HiltViewModel
public class ChatViewModel extends ViewModel {

    private final DeviceSession session;

    private final LiveData<ConnectionState> state;
    private final LiveData<List<ChatMessage>> messages;
    private final LiveData<ByteStats> stats;
    private final MutableLiveData<PayloadCodec.Format> format = new MutableLiveData<>(PayloadCodec.Format.TEXT);
    private final MutableLiveData<Boolean> inputError = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Integer>> snackbar = new MutableLiveData<>();

    @Inject
    public ChatViewModel(SavedStateHandle savedStateHandle, ConnectionManager connectionManager) {
        String address = savedStateHandle.get(ChatFragment.ARG_ADDRESS);
        if (address == null) throw new IllegalArgumentException("Cihaz adresi eksik");
        // Normalde detay ekranından gelinir ve oturum hazırdır; süreç yeniden başladıysa yeniden bağlan
        DeviceSession existing = connectionManager.session(address);
        this.session = existing != null ? existing : connectionManager.connect(address, null);
        this.state = LiveDataBridge.of(session.connection().state());
        this.messages = LiveDataBridge.of(session.messages());
        this.stats = LiveDataBridge.of(session.byteStats());
    }

    @Nullable
    public String deviceName() {
        return session.connection().getDeviceName();
    }

    public LiveData<ConnectionState> state() { return state; }
    public LiveData<List<ChatMessage>> messages() { return messages; }
    public LiveData<ByteStats> stats() { return stats; }
    public LiveData<PayloadCodec.Format> format() { return format; }
    public LiveData<Boolean> inputError() { return inputError; }
    public LiveData<Event<Integer>> snackbar() { return snackbar; }

    /**
     * Gösterim modunu değiştirir ve yazılmakta olan girdiyi yeni moda çevirir.
     * @return dönüştürülmüş girdi; çevrilemiyorsa aynı metin
     */
    public String setFormat(PayloadCodec.Format newFormat, String currentInput) {
        PayloadCodec.Format oldFormat = currentFormat();
        if (oldFormat == newFormat) return currentInput;
        format.setValue(newFormat);
        inputError.setValue(false);
        if (currentInput.isEmpty()) return currentInput;
        try {
            byte[] bytes = PayloadCodec.encode(currentInput, oldFormat);
            return newFormat == PayloadCodec.Format.HEX
                    ? PayloadCodec.toHex(bytes)
                    : new String(bytes, StandardCharsets.UTF_8);
        } catch (PayloadCodec.InvalidHexException e) {
            return currentInput;
        }
    }

    public void onInputChanged() {
        if (Boolean.TRUE.equals(inputError.getValue())) inputError.setValue(false);
    }

    /** @return gönderim kabul edildiyse true (girdi kutusu temizlenmeli) */
    public boolean send(String text) {
        if (text.isEmpty()) return false;
        byte[] payload;
        try {
            payload = PayloadCodec.encode(text, currentFormat());
        } catch (PayloadCodec.InvalidHexException e) {
            inputError.setValue(true);
            return false;
        }
        deliver(payload);
        return true;
    }

    /** Hazır komut çipleri (ör. PING) her zaman metin olarak gönderilir. */
    public void sendQuick(String text) {
        deliver(text.getBytes(StandardCharsets.UTF_8));
    }

    public void clearLog() {
        session.clearMessages();
    }

    private PayloadCodec.Format currentFormat() {
        PayloadCodec.Format value = format.getValue();
        return value != null ? value : PayloadCodec.Format.TEXT;
    }

    private void deliver(byte[] payload) {
        session.send(payload, error -> {
            if (error == null) return;
            @StringRes int message = error.type == BleError.Type.NOT_CONNECTED
                    ? R.string.chat_send_failed_not_connected
                    : R.string.chat_send_failed;
            snackbar.setValue(new Event<>(message));
        });
    }
}
