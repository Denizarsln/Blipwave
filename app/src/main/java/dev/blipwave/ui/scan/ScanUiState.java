package dev.blipwave.ui.scan;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import dev.blipwave.ble.ScanFilter;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ScannedDevice;

/** Tarama ekranının tek parça, değişmez durumu. */
public final class ScanUiState {

    public final boolean scanning;
    public final boolean demo;
    public final List<ScannedDevice> devices;
    public final int totalCount;
    public final ScanFilter filter;
    @Nullable public final BleError error;

    public ScanUiState(boolean scanning, boolean demo, List<ScannedDevice> devices, int totalCount,
                       ScanFilter filter, @Nullable BleError error) {
        this.scanning = scanning;
        this.demo = demo;
        this.devices = Collections.unmodifiableList(devices);
        this.totalCount = totalCount;
        this.filter = filter;
        this.error = error;
    }
}
