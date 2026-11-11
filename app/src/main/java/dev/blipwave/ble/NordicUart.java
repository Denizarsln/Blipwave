package dev.blipwave.ble;

import java.util.UUID;

/** Nordic UART Service (NUS) UUID'leri. RX: telefon → cihaz, TX: cihaz → telefon (notify). */
public final class NordicUart {

    public static final UUID SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID RX_CHARACTERISTIC = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID TX_CHARACTERISTIC = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    private NordicUart() { }
}
