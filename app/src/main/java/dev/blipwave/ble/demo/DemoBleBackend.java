package dev.blipwave.ble.demo;

import androidx.annotation.Nullable;

import java.util.Random;

import javax.inject.Inject;
import javax.inject.Singleton;

import dev.blipwave.ble.BleBackend;
import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.BleScanner;
import dev.blipwave.util.Clock;
import dev.blipwave.util.Scheduler;

@Singleton
public final class DemoBleBackend implements BleBackend {

    private final Scheduler scheduler;
    private final BleScanner scanner;
    private final Random random = new Random();

    @Inject
    public DemoBleBackend(Clock clock, Scheduler scheduler) {
        this.scheduler = scheduler;
        this.scanner = new DemoBleScanner(clock, scheduler);
    }

    @Override
    public boolean isDemo() {
        return true;
    }

    @Override
    public BleScanner scanner() {
        return scanner;
    }

    @Override
    public BleConnection createConnection(String address, @Nullable String name, boolean classic) {
        // Demo cihazlar profillerini katalogdan alır; klasik cihazlar zaten SPP servisi sunar
        DemoDevice device = DemoCatalog.find(address);
        return new DemoConnection(device != null ? device : DemoCatalog.fallback(address, name), scheduler, random);
    }
}
