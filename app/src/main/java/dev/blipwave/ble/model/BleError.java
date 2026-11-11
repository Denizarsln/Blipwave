package dev.blipwave.ble.model;

import java.util.Objects;

/** BLE katmanının arayüze taşıdığı hata. */
public final class BleError extends Exception {

    public enum Type {
        UNSUPPORTED, BLUETOOTH_OFF, PERMISSION_DENIED, SCAN_FAILED, NOT_CONNECTED, UART_UNAVAILABLE, WRITE_FAILED
    }

    public final Type type;
    /** Yalnızca SCAN_FAILED için sistemden gelen hata kodu. */
    public final int code;

    private BleError(Type type, int code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    public static BleError unsupported() {
        return new BleError(Type.UNSUPPORTED, 0, "Bu cihazda BLE desteklenmiyor");
    }

    public static BleError bluetoothOff() {
        return new BleError(Type.BLUETOOTH_OFF, 0, "Bluetooth kapalı");
    }

    public static BleError permissionDenied() {
        return new BleError(Type.PERMISSION_DENIED, 0, "Bluetooth izni verilmedi");
    }

    public static BleError scanFailed(int code) {
        return new BleError(Type.SCAN_FAILED, code, "Tarama başlatılamadı (kod " + code + ")");
    }

    public static BleError notConnected() {
        return new BleError(Type.NOT_CONNECTED, 0, "Cihaz bağlı değil");
    }

    public static BleError uartUnavailable() {
        return new BleError(Type.UART_UNAVAILABLE, 0, "Cihazda Nordic UART servisi yok");
    }

    public static BleError writeFailed() {
        return new BleError(Type.WRITE_FAILED, 0, "Yazma işlemi başarısız");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BleError)) return false;
        BleError that = (BleError) other;
        return type == that.type && code == that.code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, code);
    }
}
