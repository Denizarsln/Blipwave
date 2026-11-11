package dev.blipwave.data;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import dev.blipwave.util.ObservableValue;

/** Ayarları SharedPreferences'ta saklar; değeri bellekte tutup değişiklikleri yayınlar. */
@Singleton
public final class PrefsSettingsRepository implements SettingsRepository {

    private static final String FILE_NAME = "settings";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_DEMO = "demo_mode";

    private final SharedPreferences prefs;
    private final ObservableValue<AppSettings> settings;

    @Inject
    public PrefsSettingsRepository(@ApplicationContext Context context) {
        prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        settings = new ObservableValue<>(read());
    }

    @Override
    public ObservableValue<AppSettings> settings() {
        return settings;
    }

    @Override
    public void setThemeMode(AppSettings.ThemeMode mode) {
        prefs.edit().putString(KEY_THEME, mode.name()).apply();
        settings.set(settings.get().withThemeMode(mode));
    }

    @Override
    public void setDemoMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEMO, enabled).apply();
        settings.set(settings.get().withDemoMode(enabled));
    }

    private AppSettings read() {
        AppSettings.ThemeMode theme = AppSettings.DEFAULT.themeMode;
        String stored = prefs.getString(KEY_THEME, null);
        if (stored != null) {
            try {
                theme = AppSettings.ThemeMode.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // Bilinmeyen değerde varsayılan temayla devam et
            }
        }
        return new AppSettings(theme, prefs.getBoolean(KEY_DEMO, AppSettings.DEFAULT.demoMode));
    }
}
