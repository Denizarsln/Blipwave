package dev.blipwave.ble;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Bluetooth SIG standart UUID'leri ve okunabilir adları. */
public final class GattUuids {

    public static final UUID CLIENT_CONFIG_DESCRIPTOR = uuid16(0x2902);

    /** Klasik Bluetooth Serial Port Profile (SPP); RFCOMM soketi bu UUID ile açılır. */
    public static final UUID SERIAL_PORT = uuid16(0x1101);

    /** Konsol açılabilir mi: BLE'de Nordic UART servisi, klasikte SPP. */
    public static boolean supportsConsole(java.util.List<dev.blipwave.ble.model.GattServiceInfo> services) {
        for (dev.blipwave.ble.model.GattServiceInfo service : services) {
            if (NordicUart.SERVICE.equals(service.uuid) || SERIAL_PORT.equals(service.uuid)) return true;
        }
        return false;
    }

    /** Bağlantı klasik seri port mu (RSSI okunamaz, GATT yok)? */
    public static boolean isSerialPort(java.util.List<dev.blipwave.ble.model.GattServiceInfo> services) {
        for (dev.blipwave.ble.model.GattServiceInfo service : services) {
            if (SERIAL_PORT.equals(service.uuid)) return true;
        }
        return false;
    }

    private static final String BASE_SUFFIX = "-0000-1000-8000-00805F9B34FB";
    private static final Map<UUID, String> SERVICES = new HashMap<>();
    private static final Map<UUID, String> CHARACTERISTICS = new HashMap<>();

    static {
        SERVICES.put(uuid16(0x1800), "Generic Access");
        SERVICES.put(uuid16(0x1801), "Generic Attribute");
        SERVICES.put(uuid16(0x180A), "Device Information");
        SERVICES.put(uuid16(0x180F), "Battery Service");
        SERVICES.put(uuid16(0x180D), "Heart Rate");
        SERVICES.put(uuid16(0x181A), "Environmental Sensing");
        SERVICES.put(uuid16(0x1809), "Health Thermometer");
        SERVICES.put(NordicUart.SERVICE, "Nordic UART Service");
        SERVICES.put(SERIAL_PORT, "Serial Port Profile (SPP)");

        CHARACTERISTICS.put(uuid16(0x2A00), "Device Name");
        CHARACTERISTICS.put(uuid16(0x2A01), "Appearance");
        CHARACTERISTICS.put(uuid16(0x2A05), "Service Changed");
        CHARACTERISTICS.put(uuid16(0x2A19), "Battery Level");
        CHARACTERISTICS.put(uuid16(0x2A24), "Model Number");
        CHARACTERISTICS.put(uuid16(0x2A25), "Serial Number");
        CHARACTERISTICS.put(uuid16(0x2A26), "Firmware Revision");
        CHARACTERISTICS.put(uuid16(0x2A29), "Manufacturer Name");
        CHARACTERISTICS.put(uuid16(0x2A37), "Heart Rate Measurement");
        CHARACTERISTICS.put(uuid16(0x2A38), "Body Sensor Location");
        CHARACTERISTICS.put(uuid16(0x2A6E), "Temperature");
        CHARACTERISTICS.put(uuid16(0x2A6F), "Humidity");
        CHARACTERISTICS.put(NordicUart.RX_CHARACTERISTIC, "UART RX");
        CHARACTERISTICS.put(NordicUart.TX_CHARACTERISTIC, "UART TX");
    }

    private GattUuids() { }

    public static UUID uuid16(int shortId) {
        return UUID.fromString(String.format(Locale.US, "0000%04X", shortId) + BASE_SUFFIX);
    }

    @Nullable
    public static String serviceName(UUID uuid) {
        return SERVICES.get(uuid);
    }

    @Nullable
    public static String characteristicName(UUID uuid) {
        return CHARACTERISTICS.get(uuid);
    }

    /** SIG taban UUID'sindeki kısa formu ("0x180F") döndürür, değilse tam UUID'yi. */
    public static String shortLabel(UUID uuid) {
        String text = uuid.toString().toUpperCase(Locale.US);
        if (text.startsWith("0000") && text.endsWith(BASE_SUFFIX)) {
            return "0x" + text.substring(4, 8);
        }
        return text;
    }
}
