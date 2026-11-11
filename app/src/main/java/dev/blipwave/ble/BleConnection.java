package dev.blipwave.ble;

import androidx.annotation.Nullable;

import java.util.List;

import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.util.EventStream;
import dev.blipwave.util.ObservableValue;

/** Tek bir cihaza GATT bağlantısı. Gerçek ve demo uygulamaları aynı sözleşmeyi paylaşır. */
public interface BleConnection {

    interface SendCallback {
        /** @param error başarılıysa null */
        void onComplete(@Nullable BleError error);
    }

    String getAddress();

    @Nullable
    String getDeviceName();

    ObservableValue<ConnectionState> state();

    ObservableValue<List<GattServiceInfo>> services();

    /** Bağlıyken yaklaşık saniyede bir RSSI örneği. */
    EventStream<Integer> rssi();

    /** Nordic UART TX karakteristiğinden gelen ham veri. */
    EventStream<byte[]> incoming();

    void connect();

    void disconnect();

    /** UART RX karakteristiğine yazar; MTU'ya göre parçalara bölmek uygulamanın işidir. */
    void send(byte[] data, SendCallback callback);

    /** Kaynakları serbest bırakır; sonrasında nesne yeniden kullanılmaz. */
    void close();
}
