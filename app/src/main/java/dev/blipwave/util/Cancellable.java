package dev.blipwave.util;

/** İptal edilebilen bir abonelik veya zamanlanmış iş. */
public interface Cancellable {
    void cancel();

    Cancellable NONE = () -> { };
}
