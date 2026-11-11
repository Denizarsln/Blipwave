package dev.blipwave;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;
import dev.blipwave.data.AppSettings;
import dev.blipwave.data.SettingsRepository;

@HiltAndroidApp
public class BlipwaveApplication extends Application {

    @Inject SettingsRepository settingsRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Tema tercihi değiştikçe tüm aktiviteler doğru gece moduyla yeniden oluşturulur
        settingsRepository.settings().observe(settings -> AppCompatDelegate.setDefaultNightMode(nightModeFor(settings)));
    }

    static int nightModeFor(AppSettings settings) {
        switch (settings.themeMode) {
            case LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case SYSTEM:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            case DARK:
            default:
                return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }
}
