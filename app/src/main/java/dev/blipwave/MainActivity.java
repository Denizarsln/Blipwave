package dev.blipwave;

import android.os.Bundle;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import dagger.hilt.android.AndroidEntryPoint;
import dev.blipwave.databinding.ActivityMainBinding;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (host == null) throw new IllegalStateException("NavHostFragment bulunamadı");
        NavController navController = host.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
        // Aynı sekmeye tekrar dokunmak ekranı yeniden oluşturmasın
        binding.bottomNav.setOnItemReselectedListener(item -> { });

        // Alt menü yalnızca üst seviye ekranlarda görünür; detayda aşağı kayarak kaybolur
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean topLevel = id == R.id.scanFragment || id == R.id.connectionsFragment || id == R.id.settingsFragment;
            setBottomBarVisible(topLevel);
        });
    }

    private void setBottomBarVisible(boolean visible) {
        float hiddenOffset = binding.bottomBar.getHeight() + binding.bottomBar.getPaddingBottom() + 200f;
        binding.bottomBar.animate().cancel();
        if (visible) {
            binding.bottomBar.setVisibility(android.view.View.VISIBLE);
            binding.bottomBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(320L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            binding.bottomBar.animate()
                    .translationY(hiddenOffset)
                    .alpha(0f)
                    .setDuration(320L)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> binding.bottomBar.setVisibility(android.view.View.GONE))
                    .start();
        }
    }
}
