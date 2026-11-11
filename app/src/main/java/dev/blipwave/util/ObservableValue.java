package dev.blipwave.util;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Android'e bağımlı olmayan basit durum tutucu. Değer değişince dinleyicilere bildirir;
 * abone olunduğu an mevcut değeri de iletir. Arayüze LiveDataBridge ile bağlanır.
 */
public class ObservableValue<T> {

    private final CopyOnWriteArrayList<Listener<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    public ObservableValue(T initial) {
        this.value = initial;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        if (Objects.equals(value, newValue)) return;
        value = newValue;
        for (Listener<T> listener : listeners) listener.onValue(newValue);
    }

    public Cancellable observe(Listener<T> listener) {
        listeners.add(listener);
        listener.onValue(value);
        return () -> listeners.remove(listener);
    }
}
