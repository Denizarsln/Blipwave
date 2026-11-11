package dev.blipwave.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import dagger.hilt.android.AndroidEntryPoint;
import dev.blipwave.BuildConfig;
import dev.blipwave.R;
import dev.blipwave.data.AppSettings;
import dev.blipwave.databinding.FragmentSettingsBinding;
import dev.blipwave.ui.components.Ui;

@AndroidEntryPoint
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        Context context = requireContext();
        Ui.applyBrandGradient(binding.title);

        binding.aboutBadges.addView(Ui.badge(context, getString(R.string.settings_version, BuildConfig.VERSION_NAME),
                Ui.color(context, R.color.bp_primary)), Ui.wrapWithEndMargin(context, 8));
        binding.aboutBadges.addView(Ui.badge(context, getString(R.string.settings_license),
                Ui.color(context, R.color.bp_tertiary)), Ui.wrapWithEndMargin(context, 8));

        binding.themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.theme_light) viewModel.setThemeMode(AppSettings.ThemeMode.LIGHT);
            else if (checkedId == R.id.theme_system) viewModel.setThemeMode(AppSettings.ThemeMode.SYSTEM);
            else viewModel.setThemeMode(AppSettings.ThemeMode.DARK);
        });
        binding.demoRow.setOnClickListener(v -> viewModel.toggleDemoMode());

        viewModel.settings().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(AppSettings settings) {
        int checked;
        switch (settings.themeMode) {
            case LIGHT: checked = R.id.theme_light; break;
            case SYSTEM: checked = R.id.theme_system; break;
            case DARK:
            default: checked = R.id.theme_dark; break;
        }
        if (binding.themeGroup.getCheckedButtonId() != checked) binding.themeGroup.check(checked);
        binding.demoSwitch.setChecked(settings.demoMode);
    }
}
