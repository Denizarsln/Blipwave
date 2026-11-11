package dev.blipwave.util;

import android.os.Looper;

import androidx.lifecycle.LiveData;

/** ObservableValue'yu yaşam döngüsüne duyarlı LiveData'ya çevirir; yalnızca gözlemci varken abone olur. */
public final class LiveDataBridge {

    private LiveDataBridge() { }

    public static <T> LiveData<T> of(ObservableValue<T> source) {
        return new LiveData<T>(source.get()) {
            private Cancellable subscription = Cancellable.NONE;

            @Override
            protected void onActive() {
                subscription = source.observe(this::publish);
            }

            @Override
            protected void onInactive() {
                subscription.cancel();
                subscription = Cancellable.NONE;
            }

            private void publish(T value) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    setValue(value);
                } else {
                    postValue(value);
                }
            }
        };
    }
}
