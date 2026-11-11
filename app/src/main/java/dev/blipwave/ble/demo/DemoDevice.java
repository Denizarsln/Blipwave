package dev.blipwave.ble.demo;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import dev.blipwave.ble.GattUuids;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.ble.model.ScannedDevice;

/** Demo modunda görünen sahte bir cihazın tarifi. */
public final class DemoDevice {

    public final String address;
    @Nullable public final String name;
    public final int baseRssi;
    public final List<GattServiceInfo> services;
    /** Belirli aralıklarla kendiliğinden telemetri mesajı gönderir. */
    public final boolean sendsTelemetry;
    /** 0'dan büyükse bağlantı bu süre sonunda beklenmedik şekilde kopar. */
    public final long dropsAfterMillis;
    public final ScannedDevice.Transport transport;
    public final ScannedDevice.Category category;

    public DemoDevice(String address, @Nullable String name, int baseRssi, List<GattServiceInfo> services,
                      boolean sendsTelemetry, long dropsAfterMillis,
                      ScannedDevice.Transport transport, ScannedDevice.Category category) {
        this.address = address;
        this.name = name;
        this.baseRssi = baseRssi;
        this.services = Collections.unmodifiableList(services);
        this.sendsTelemetry = sendsTelemetry;
        this.dropsAfterMillis = dropsAfterMillis;
        this.transport = transport;
        this.category = category;
    }

    public DemoDevice(String address, @Nullable String name, int baseRssi, List<GattServiceInfo> services,
                      boolean sendsTelemetry, long dropsAfterMillis) {
        this(address, name, baseRssi, services, sendsTelemetry, dropsAfterMillis,
                ScannedDevice.Transport.LE, ScannedDevice.Category.UNKNOWN);
    }

    public DemoDevice(String address, @Nullable String name, int baseRssi, List<GattServiceInfo> services) {
        this(address, name, baseRssi, services, false, 0);
    }

    /** Yalnız klasik Bluetooth ile görünen cihaz; seri port (SPP) ile bağlanılır. */
    public static DemoDevice classic(String address, String name, int baseRssi, ScannedDevice.Category category) {
        return new DemoDevice(address, name, baseRssi,
                Collections.singletonList(new GattServiceInfo(GattUuids.SERIAL_PORT, Collections.emptyList())),
                false, 0, ScannedDevice.Transport.CLASSIC, category);
    }

    /** Konsol destekli mi: Nordic UART (BLE) veya seri port (klasik SPP). */
    public boolean hasUart() {
        return GattUuids.supportsConsole(services);
    }
}
