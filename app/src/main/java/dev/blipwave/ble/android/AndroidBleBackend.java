package dev.blipwave.ble.android;

import android.content.Context;

import androidx.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import dev.blipwave.ble.BleBackend;
import dev.blipwave.ble.BleConnection;
import dev.blipwave.ble.BleScanner;
import dev.blipwave.util.Clock;
import dev.blipwave.util.Scheduler;

@Singleton
public final class AndroidBleBackend implements BleBackend {

    private final Context context;
    private final Scheduler scheduler;
    private final BleScanner scanner;

    @Inject
    public AndroidBleBackend(@ApplicationContext Context context, Clock clock, Scheduler scheduler) {
        this.context = context;
        this.scheduler = scheduler;
        this.scanner = new AndroidBleScanner(context, clock, scheduler);
    }

    @Override
    public boolean isDemo() {
        return false;
    }

    @Override
    public BleScanner scanner() {
        return scanner;
    }

    @Override
    public BleConnection createConnection(String address, @Nullable String name, boolean classic) {
        if (classic) return new ClassicSppConnection(context, address, name, scheduler);
        return new AndroidGattConnection(context, address, name, scheduler);
    }
}
