package dev.blipwave.data;

import java.util.Objects;

public final class AppSettings {

    public enum ThemeMode { DARK, LIGHT, SYSTEM }

    public static final AppSettings DEFAULT = new AppSettings(ThemeMode.DARK, false);

    public final ThemeMode themeMode;
    public final boolean demoMode;

    public AppSettings(ThemeMode themeMode, boolean demoMode) {
        this.themeMode = themeMode;
        this.demoMode = demoMode;
    }

    public AppSettings withThemeMode(ThemeMode value) {
        return new AppSettings(value, demoMode);
    }

    public AppSettings withDemoMode(boolean value) {
        return new AppSettings(themeMode, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AppSettings)) return false;
        AppSettings that = (AppSettings) other;
        return demoMode == that.demoMode && themeMode == that.themeMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(themeMode, demoMode);
    }
}
