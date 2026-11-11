package dev.blipwave.ble.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Keşfedilen bir GATT servisi ve characteristic'leri. */
public final class GattServiceInfo {

    public enum Property { READ, WRITE, WRITE_NO_RESPONSE, NOTIFY, INDICATE }

    public static final class Characteristic {
        public final UUID uuid;
        public final Set<Property> properties;

        public Characteristic(UUID uuid, Set<Property> properties) {
            this.uuid = uuid;
            this.properties = Collections.unmodifiableSet(
                    properties.isEmpty() ? EnumSet.noneOf(Property.class) : EnumSet.copyOf(properties));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Characteristic)) return false;
            Characteristic that = (Characteristic) other;
            return uuid.equals(that.uuid) && properties.equals(that.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, properties);
        }
    }

    public final UUID uuid;
    public final List<Characteristic> characteristics;

    public GattServiceInfo(UUID uuid, List<Characteristic> characteristics) {
        this.uuid = uuid;
        this.characteristics = Collections.unmodifiableList(characteristics);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GattServiceInfo)) return false;
        GattServiceInfo that = (GattServiceInfo) other;
        return uuid.equals(that.uuid) && characteristics.equals(that.characteristics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, characteristics);
    }
}
