package dev.blipwave.ble;

import java.util.Objects;

/** Değişmez tarama filtresi; her "with" çağrısı yeni bir nesne döndürür. */
public final class ScanFilter {

    public enum SortOrder { SIGNAL, NAME, RECENT }

    /** Hangi tür cihazların gösterileceği. Çift mod cihazlar her iki seçimde de görünür. */
    public enum TransportFilter { ALL, LE, CLASSIC }

    public static final int MIN_RSSI_FLOOR = -100;
    public static final int MAX_RSSI_CEILING = -30;

    public final boolean namedOnly;
    public final int minRssi;
    public final SortOrder sortOrder;
    public final TransportFilter transport;

    public ScanFilter() {
        this(false, MIN_RSSI_FLOOR, SortOrder.SIGNAL, TransportFilter.ALL);
    }

    public ScanFilter(boolean namedOnly, int minRssi, SortOrder sortOrder, TransportFilter transport) {
        this.namedOnly = namedOnly;
        this.minRssi = Math.max(MIN_RSSI_FLOOR, Math.min(MAX_RSSI_CEILING, minRssi));
        this.sortOrder = sortOrder;
        this.transport = transport;
    }

    public ScanFilter withNamedOnly(boolean value) {
        return new ScanFilter(value, minRssi, sortOrder, transport);
    }

    public ScanFilter withMinRssi(int value) {
        return new ScanFilter(namedOnly, value, sortOrder, transport);
    }

    public ScanFilter withSortOrder(SortOrder value) {
        return new ScanFilter(namedOnly, minRssi, value, transport);
    }

    public ScanFilter withTransport(TransportFilter value) {
        return new ScanFilter(namedOnly, minRssi, sortOrder, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScanFilter)) return false;
        ScanFilter that = (ScanFilter) other;
        return namedOnly == that.namedOnly && minRssi == that.minRssi && sortOrder == that.sortOrder
                && transport == that.transport;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namedOnly, minRssi, sortOrder, transport);
    }
}
