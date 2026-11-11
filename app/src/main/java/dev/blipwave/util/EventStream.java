package dev.blipwave.util;

import java.util.concurrent.CopyOnWriteArrayList;

/** Değeri saklanmayan olay akışı (gelen bayt, RSSI örneği). Sadece o an abone olanlar alır. */
public class EventStream<T> {

    private final CopyOnWriteArrayList<Listener<T>> listeners = new CopyOnWriteArrayList<>();

    public void emit(T event) {
        for (Listener<T> listener : listeners) listener.onValue(event);
    }

    public Cancellable subscribe(Listener<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
