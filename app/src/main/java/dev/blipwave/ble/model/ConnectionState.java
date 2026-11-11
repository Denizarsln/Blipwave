package dev.blipwave.ble.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Bağlantı yaşam döngüsü. Arayüzde Bağlanıyor / Bağlandı / Koptu olarak gösterilir. */
public final class ConnectionState {

    public enum Status { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

    public static final ConnectionState IDLE = new ConnectionState(Status.IDLE, false, null);
    public static final ConnectionState CONNECTING = new ConnectionState(Status.CONNECTING, false, null);
    public static final ConnectionState CONNECTED = new ConnectionState(Status.CONNECTED, false, null);

    public final Status status;
    /** Kullanıcı istemeden kopma (menzil dışı, zaman aşımı, GATT hatası). */
    public final boolean unexpected;
    @Nullable public final String reason;

    private ConnectionState(Status status, boolean unexpected, @Nullable String reason) {
        this.status = status;
        this.unexpected = unexpected;
        this.reason = reason;
    }

    public static ConnectionState disconnected(boolean unexpected, @Nullable String reason) {
        return new ConnectionState(Status.DISCONNECTED, unexpected, reason);
    }

    public static ConnectionState disconnectedByUser() {
        return disconnected(false, null);
    }

    public boolean isActive() {
        return status == Status.CONNECTING || status == Status.CONNECTED;
    }

    public boolean isConnected() {
        return status == Status.CONNECTED;
    }

    public boolean isLost() {
        return status == Status.DISCONNECTED && unexpected;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConnectionState)) return false;
        ConnectionState that = (ConnectionState) other;
        return status == that.status && unexpected == that.unexpected && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, unexpected, reason);
    }

    @Override
    public String toString() {
        return "ConnectionState{" + status + (unexpected ? ", unexpected" : "") + (reason != null ? ", " + reason : "") + "}";
    }
}
