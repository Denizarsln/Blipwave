package dev.blipwave.ble;

import androidx.annotation.Nullable;

/** Tarayıcı ve bağlantı fabrikasını bir arada sunar (gerçek donanım veya demo). */
public interface BleBackend {

    boolean isDemo();

    BleScanner scanner();

    /**
     * @param classic true ise klasik Bluetooth seri port (SPP) bağlantısı, değilse BLE GATT bağlantısı kurulur
     */
    BleConnection createConnection(String address, @Nullable String name, boolean classic);
}
