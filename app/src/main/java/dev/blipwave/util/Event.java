package dev.blipwave.util;

import androidx.annotation.Nullable;

/** LiveData üzerinden bir kez tüketilen olay (ör. Snackbar mesajı); döndürmede tekrar gösterilmez. */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
