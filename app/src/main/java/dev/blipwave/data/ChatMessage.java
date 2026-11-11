package dev.blipwave.data;

/** Sohbet ekranındaki tek mesaj. İçerik ham bayt olarak saklanır, gösterim moduna göre çözülür. */
public final class ChatMessage {

    public enum Direction { TX, RX }

    public final long id;
    public final Direction direction;
    private final byte[] payload;
    public final long timestampMillis;

    public ChatMessage(long id, Direction direction, byte[] payload, long timestampMillis) {
        this.id = id;
        this.direction = direction;
        this.payload = payload.clone();
        this.timestampMillis = timestampMillis;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int size() {
        return payload.length;
    }
}
