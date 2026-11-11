package dev.blipwave.data;

import dev.blipwave.util.ObservableValue;

public interface SettingsRepository {

    ObservableValue<AppSettings> settings();

    void setThemeMode(AppSettings.ThemeMode mode);

    void setDemoMode(boolean enabled);
}
