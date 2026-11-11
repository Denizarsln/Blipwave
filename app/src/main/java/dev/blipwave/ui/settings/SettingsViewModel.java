package dev.blipwave.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dev.blipwave.data.AppSettings;
import dev.blipwave.data.SettingsRepository;
import dev.blipwave.util.LiveDataBridge;

@HiltViewModel
public class SettingsViewModel extends ViewModel {

    private final SettingsRepository repository;
    private final LiveData<AppSettings> settings;

    @Inject
    public SettingsViewModel(SettingsRepository repository) {
        this.repository = repository;
        this.settings = LiveDataBridge.of(repository.settings());
    }

    public LiveData<AppSettings> settings() {
        return settings;
    }

    public void setThemeMode(AppSettings.ThemeMode mode) {
        repository.setThemeMode(mode);
    }

    public void toggleDemoMode() {
        repository.setDemoMode(!repository.settings().get().demoMode);
    }
}
