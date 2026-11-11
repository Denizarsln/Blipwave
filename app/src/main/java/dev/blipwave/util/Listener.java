package dev.blipwave.util;

public interface Listener<T> {
    void onValue(T value);
}
