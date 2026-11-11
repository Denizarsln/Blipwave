package dev.blipwave.ble;

import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.util.Cancellable;

/** Taramayı başlatır; dönen nesne iptal edilince tarama durur. */
public interface BleScanner {

    interface Listener {
        void onDevice(ScannedDevice device);

        /** Hata sonrası tarama kendiliğinden durmuştur. */
        void onError(BleError error);
    }

    Cancellable start(Listener listener);
}
