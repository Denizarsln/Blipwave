package dev.blipwave.ble.demo;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import dev.blipwave.ble.GattUuids;
import dev.blipwave.ble.NordicUart;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.ble.model.GattServiceInfo.Property;
import dev.blipwave.ble.model.ScannedDevice;

/** Demo modunun sabit cihaz listesi. */
public final class DemoCatalog {

    private static final GattServiceInfo GENERIC_ACCESS = service(0x1800,
            characteristic(GattUuids.uuid16(0x2A00), Property.READ),
            characteristic(GattUuids.uuid16(0x2A01), Property.READ));
    private static final GattServiceInfo GENERIC_ATTRIBUTE = service(0x1801,
            characteristic(GattUuids.uuid16(0x2A05), Property.INDICATE));
    private static final GattServiceInfo DEVICE_INFO = service(0x180A,
            characteristic(GattUuids.uuid16(0x2A29), Property.READ),
            characteristic(GattUuids.uuid16(0x2A24), Property.READ),
            characteristic(GattUuids.uuid16(0x2A26), Property.READ));
    private static final GattServiceInfo BATTERY = service(0x180F,
            characteristic(GattUuids.uuid16(0x2A19), Property.READ, Property.NOTIFY));
    private static final GattServiceInfo HEART_RATE = service(0x180D,
            characteristic(GattUuids.uuid16(0x2A37), Property.NOTIFY),
            characteristic(GattUuids.uuid16(0x2A38), Property.READ));
    private static final GattServiceInfo ENVIRONMENT = service(0x181A,
            characteristic(GattUuids.uuid16(0x2A6E), Property.READ, Property.NOTIFY),
            characteristic(GattUuids.uuid16(0x2A6F), Property.READ, Property.NOTIFY));
    private static final GattServiceInfo UART = new GattServiceInfo(NordicUart.SERVICE, Arrays.asList(
            characteristic(NordicUart.RX_CHARACTERISTIC, Property.WRITE, Property.WRITE_NO_RESPONSE),
            characteristic(NordicUart.TX_CHARACTERISTIC, Property.NOTIFY)));

    public static final List<DemoDevice> DEVICES = Collections.unmodifiableList(Arrays.asList(
            new DemoDevice("D0:0D:1E:00:00:01", "Blipwave Echo", -47,
                    Arrays.asList(GENERIC_ACCESS, GENERIC_ATTRIBUTE, DEVICE_INFO, BATTERY, UART)),
            new DemoDevice("D0:0D:1E:00:00:02", "ESP32 UART", -58,
                    Arrays.asList(GENERIC_ACCESS, GENERIC_ATTRIBUTE, UART)),
            new DemoDevice("D0:0D:1E:00:00:03", "Pulse Band", -66,
                    Arrays.asList(GENERIC_ACCESS, DEVICE_INFO, BATTERY, HEART_RATE)),
            new DemoDevice("D0:0D:1E:00:00:04", "Thermo Node", -73,
                    Arrays.asList(GENERIC_ACCESS, ENVIRONMENT, UART), true, 0),
            new DemoDevice("D0:0D:1E:00:00:05", null, -81,
                    Arrays.asList(GENERIC_ACCESS, GENERIC_ATTRIBUTE)),
            new DemoDevice("D0:0D:1E:00:00:06", "Flaky Beacon", -87,
                    Arrays.asList(GENERIC_ACCESS, UART), false, 20_000L),
            new DemoDevice("D0:0D:1E:00:00:07", null, -93,
                    Collections.singletonList(GENERIC_ACCESS)),
            // Klasik Bluetooth cihazları: listede görünür, GATT ile bağlanılamaz
            DemoDevice.classic("D0:0D:1E:00:00:08", "Office Headset", -62, ScannedDevice.Category.AUDIO_VIDEO),
            // Hem BLE hem klasik yayın yapan telefon: çift mod olarak görünür
            new DemoDevice("D0:0D:1E:00:00:09", "Pixel Phone", -55,
                    Arrays.asList(GENERIC_ACCESS, GENERIC_ATTRIBUTE, BATTERY), false, 0,
                    ScannedDevice.Transport.DUAL, ScannedDevice.Category.PHONE),
            DemoDevice.classic("D0:0D:1E:00:00:0A", "Car Kit", -84, ScannedDevice.Category.AUDIO_VIDEO)
    ));

    private DemoCatalog() { }

    @Nullable
    public static DemoDevice find(String address) {
        for (DemoDevice device : DEVICES) {
            if (device.address.equals(address)) return device;
        }
        return null;
    }

    /** Katalogda olmayan bir adres için en sade profil. */
    public static DemoDevice fallback(String address, @Nullable String name) {
        return new DemoDevice(address, name, -70, Collections.singletonList(GENERIC_ACCESS));
    }

    private static GattServiceInfo service(int shortId, GattServiceInfo.Characteristic... characteristics) {
        return new GattServiceInfo(GattUuids.uuid16(shortId), Arrays.asList(characteristics));
    }

    private static GattServiceInfo.Characteristic characteristic(UUID uuid, Property first, Property... rest) {
        return new GattServiceInfo.Characteristic(uuid, EnumSet.of(first, rest));
    }
}
