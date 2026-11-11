package dev.blipwave.di;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dev.blipwave.ble.BleBackendProvider;
import dev.blipwave.data.PrefsSettingsRepository;
import dev.blipwave.data.SettingsBackendProvider;
import dev.blipwave.data.SettingsRepository;
import dev.blipwave.util.Clock;
import dev.blipwave.util.HandlerScheduler;
import dev.blipwave.util.Scheduler;

@Module
@InstallIn(SingletonComponent.class)
public abstract class AppModule {

    @Provides
    @Singleton
    static Clock provideClock() {
        return Clock.SYSTEM;
    }

    @Provides
    @Singleton
    static Scheduler provideScheduler() {
        return new HandlerScheduler();
    }

    @Binds
    abstract SettingsRepository bindSettingsRepository(PrefsSettingsRepository impl);

    @Binds
    abstract BleBackendProvider bindBackendProvider(SettingsBackendProvider impl);
}
