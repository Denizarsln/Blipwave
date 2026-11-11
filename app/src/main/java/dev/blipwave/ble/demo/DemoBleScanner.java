package dev.blipwave.ble.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import dev.blipwave.ble.BleScanner;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.util.Cancellable;
import dev.blipwave.util.Clock;
import dev.blipwave.util.Scheduler;

/** Sahte cihazları tek tek "keşfeder" ve RSSI değerlerini dalgalandırır. */
public final class DemoBleScanner implements BleScanner {

    public static final long INTERVAL_MILLIS = 450L;

    private final Clock clock;
    private final Scheduler scheduler;
    private final Random random;
    private final List<DemoDevice> devices;

    public DemoBleScanner(Clock clock, Scheduler scheduler, Random random, List<DemoDevice> devices) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.random = random;
        this.devices = devices;
    }

    public DemoBleScanner(Clock clock, Scheduler scheduler) {
        this(clock, scheduler, new Random(), DemoCatalog.DEVICES);
    }

    @Override
    public Cancellable start(Listener listener) {
        Map<String, Integer> levels = new HashMap<>();
        for (DemoDevice device : devices) levels.put(device.address, device.baseRssi);
        final int[] visibleCount = {0};

        return scheduler.scheduleRepeating(() -> {
            // Her turda bir cihaz daha görünür olur; kartlar sırayla belirir
            if (visibleCount[0] < devices.size()) visibleCount[0]++;
            for (int index = 0; index < visibleCount[0]; index++) {
                DemoDevice device = devices.get(index);
                int next = levels.get(device.address) + random.nextInt(7) - 3;
                next = Math.max(device.baseRssi - 10, Math.min(device.baseRssi + 8, next));
                levels.put(device.address, next);
                listener.onDevice(new ScannedDevice(
                        device.address, device.name, next, clock.nowMillis(),
                        device.transport != ScannedDevice.Transport.CLASSIC, uuidsOf(device),
                        device.transport, device.category));
            }
        }, 0, INTERVAL_MILLIS);
    }

    private static List<UUID> uuidsOf(DemoDevice device) {
        List<UUID> uuids = new ArrayList<>();
        // Klasik keşif servis UUID'si bildirmez; SPP yalnızca bağlanınca görünür
        if (device.transport == ScannedDevice.Transport.CLASSIC) return uuids;
        for (GattServiceInfo service : device.services) uuids.add(service.uuid);
        return uuids;
    }
}
