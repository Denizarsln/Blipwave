package dev.blipwave.ble;

import dev.blipwave.util.ObservableValue;

/** O anki ayara göre etkin backend'i verir. */
public interface BleBackendProvider {
    ObservableValue<BleBackend> backend();
}
