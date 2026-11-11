package dev.blipwave.ble;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.blipwave.ble.model.ScannedDevice;

/** Tarama sonuçlarını birleştiren, eskiyenleri ayıklayan ve filtreleyen saf fonksiyonlar. */
public final class ScanResults {

    /** Bir cihaz 10 sn görünmezse listeden düşer. */
    public static final long STALE_AFTER_MILLIS = 10_000L;

    private ScanResults() { }

    /**
     * Klasik keşif bir turu ~12 sn sürer ve cihazı tur başına bir kez bildirir;
     * bu yüzden yalnız klasik görülen cihazlar daha geç düşürülür.
     */
    public static final long CLASSIC_STALE_AFTER_MILLIS = 30_000L;

    /**
     * Yeni sonucu mevcut haritaya işler (girdiyi değiştirmez).
     * <ul>
     *   <li>Yeni pakette isim yoksa eski isim korunur (bazı cihazlar ismi yalnız scan response'ta gönderir).</li>
     *   <li>Aynı adres hem BLE hem klasik taramada görülürse cihaz çift mod (DUAL) olur.</li>
     *   <li>Klasik keşif BLE verisini (servisler, bağlanılabilirlik) bilmez; bunlar önceki kayıttan korunur.</li>
     * </ul>
     */
    public static Map<String, ScannedDevice> merge(Map<String, ScannedDevice> current, ScannedDevice update) {
        Map<String, ScannedDevice> result = new LinkedHashMap<>(current);
        ScannedDevice previous = current.get(update.address);
        ScannedDevice merged = update;
        if (previous != null) {
            boolean classicUpdate = update.transport == ScannedDevice.Transport.CLASSIC;
            merged = new ScannedDevice(
                    update.address,
                    update.hasName() ? update.name : previous.name,
                    update.rssi,
                    update.lastSeenMillis,
                    classicUpdate ? previous.connectable : update.connectable,
                    classicUpdate ? previous.serviceUuids : update.serviceUuids,
                    combine(previous.transport, update.transport),
                    update.category != ScannedDevice.Category.UNKNOWN ? update.category : previous.category);
        }
        result.put(update.address, merged);
        return result;
    }

    private static ScannedDevice.Transport combine(ScannedDevice.Transport a, ScannedDevice.Transport b) {
        return a == b ? a : ScannedDevice.Transport.DUAL;
    }

    /** @param staleAfterMillis BLE (ve çift mod) cihazlar için pencere; yalnız klasik cihazlar daha uzun tutulur */
    public static Map<String, ScannedDevice> pruneStale(Map<String, ScannedDevice> current, long nowMillis,
                                                        long staleAfterMillis) {
        Map<String, ScannedDevice> result = new LinkedHashMap<>();
        for (Map.Entry<String, ScannedDevice> entry : current.entrySet()) {
            ScannedDevice device = entry.getValue();
            long window = device.transport == ScannedDevice.Transport.CLASSIC
                    ? Math.max(staleAfterMillis, CLASSIC_STALE_AFTER_MILLIS)
                    : staleAfterMillis;
            if (nowMillis - device.lastSeenMillis <= window) {
                result.put(entry.getKey(), device);
            }
        }
        return result;
    }

    public static Map<String, ScannedDevice> pruneStale(Map<String, ScannedDevice> current, long nowMillis) {
        return pruneStale(current, nowMillis, STALE_AFTER_MILLIS);
    }

    public static List<ScannedDevice> apply(Collection<ScannedDevice> devices, ScanFilter filter) {
        List<ScannedDevice> result = new ArrayList<>();
        for (ScannedDevice device : devices) {
            if (filter.namedOnly && !device.hasName()) continue;
            if (device.rssi < filter.minRssi) continue;
            if (filter.transport == ScanFilter.TransportFilter.LE
                    && device.transport == ScannedDevice.Transport.CLASSIC) continue;
            if (filter.transport == ScanFilter.TransportFilter.CLASSIC
                    && device.transport == ScannedDevice.Transport.LE) continue;
            result.add(device);
        }
        result.sort(comparatorFor(filter.sortOrder));
        return result;
    }

    private static Comparator<ScannedDevice> comparatorFor(ScanFilter.SortOrder order) {
        Comparator<ScannedDevice> byAddress = Comparator.comparing(device -> device.address);
        switch (order) {
            case NAME:
                return Comparator.<ScannedDevice, Boolean>comparing(device -> !device.hasName())
                        .thenComparing(device -> device.name == null ? "" : device.name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(byAddress);
            case RECENT:
                return Comparator.<ScannedDevice>comparingLong(device -> device.lastSeenMillis).reversed()
                        .thenComparing(byAddress);
            case SIGNAL:
            default:
                return Comparator.<ScannedDevice>comparingInt(device -> device.rssi).reversed()
                        .thenComparing(byAddress);
        }
    }
}
