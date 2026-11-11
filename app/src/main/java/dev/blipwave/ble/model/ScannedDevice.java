package dev.blipwave.ble.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Taramada görülen bir cihazın özeti (BLE reklamı ve/veya klasik Bluetooth keşfi). */
public final class ScannedDevice {

    /** Cihazın hangi taramada görüldüğü. İkisinde de görülen cihaz çift moddur. */
    public enum Transport { LE, CLASSIC, DUAL }

    /** Klasik Bluetooth cihaz sınıfının ana grubu (BluetoothClass.Device.Major). */
    public enum Category { UNKNOWN, AUDIO_VIDEO, PHONE, COMPUTER, WEARABLE, PERIPHERAL, HEALTH, IMAGING, NETWORKING, TOY }

    public final String address;
    @Nullable public final String name;
    public final int rssi;
    public final long lastSeenMillis;
    /** BLE reklamının bağlanılabilir olup olmadığı; yalnız klasik cihazlarda false. */
    public final boolean connectable;
    public final List<UUID> serviceUuids;
    public final Transport transport;
    public final Category category;

    public ScannedDevice(String address, @Nullable String name, int rssi, long lastSeenMillis,
                         boolean connectable, List<UUID> serviceUuids, Transport transport, Category category) {
        this.address = address;
        this.name = name;
        this.rssi = rssi;
        this.lastSeenMillis = lastSeenMillis;
        this.connectable = connectable;
        this.serviceUuids = Collections.unmodifiableList(serviceUuids);
        this.transport = transport;
        this.category = category;
    }

    /** BLE reklamından gelen cihaz. */
    public ScannedDevice(String address, @Nullable String name, int rssi, long lastSeenMillis,
                         boolean connectable, List<UUID> serviceUuids) {
        this(address, name, rssi, lastSeenMillis, connectable, serviceUuids, Transport.LE, Category.UNKNOWN);
    }

    public ScannedDevice(String address, @Nullable String name, int rssi, long lastSeenMillis) {
        this(address, name, rssi, lastSeenMillis, true, Collections.emptyList());
    }

    /** Klasik Bluetooth keşfinden gelen cihaz; GATT ile bağlanılamaz. */
    public static ScannedDevice classic(String address, @Nullable String name, int rssi, long lastSeenMillis,
                                        Category category) {
        return new ScannedDevice(address, name, rssi, lastSeenMillis, false, Collections.emptyList(),
                Transport.CLASSIC, category);
    }

    public boolean hasName() {
        return name != null && !name.trim().isEmpty();
    }

    public boolean isClassic() {
        return transport == Transport.CLASSIC || transport == Transport.DUAL;
    }

    /** Bağlanılabilir mi: klasik cihazlar seri port (SPP) ile, BLE cihazlar GATT ile. */
    public boolean canConnect() {
        return transport == Transport.CLASSIC || connectable;
    }

    /** GATT bağlantısı (servis listesi, UART) yalnızca BLE tarafı olan cihazlarda mümkün. */
    public boolean canConnectGatt() {
        return transport != Transport.CLASSIC && connectable;
    }

    public ScannedDevice withName(@Nullable String newName) {
        return new ScannedDevice(address, newName, rssi, lastSeenMillis, connectable, serviceUuids, transport, category);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScannedDevice)) return false;
        ScannedDevice that = (ScannedDevice) other;
        return rssi == that.rssi
                && lastSeenMillis == that.lastSeenMillis
                && connectable == that.connectable
                && address.equals(that.address)
                && Objects.equals(name, that.name)
                && serviceUuids.equals(that.serviceUuids)
                && transport == that.transport
                && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, name, rssi, lastSeenMillis, connectable, serviceUuids, transport, category);
    }
}
