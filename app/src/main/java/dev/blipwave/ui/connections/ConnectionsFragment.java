package dev.blipwave.ui.connections;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;
import dev.blipwave.R;
import dev.blipwave.data.DeviceSession;
import dev.blipwave.databinding.FragmentConnectionsBinding;
import dev.blipwave.ui.components.Ui;
import dev.blipwave.ui.device.DeviceDetailFragment;

@AndroidEntryPoint
public class ConnectionsFragment extends Fragment {

    private FragmentConnectionsBinding binding;
    private ConnectionsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentConnectionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ConnectionsViewModel.class);
        Ui.applyBrandGradient(binding.title);
        NavController navController = NavHostFragment.findNavController(this);

        SessionAdapter adapter = new SessionAdapter(new SessionAdapter.Actions() {
            @Override
            public void onOpen(DeviceSession session) {
                navController.navigate(R.id.action_global_device, DeviceDetailFragment.args(
                        session.connection().getAddress(), session.connection().getDeviceName()));
            }

            @Override
            public void onForget(DeviceSession session) {
                viewModel.forget(session.connection().getAddress());
            }
        });
        binding.sessionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.sessionList.setAdapter(adapter);

        // Alt menüdeki "Tara" sekmesine geçer; sekme durumu korunur
        binding.goScan.setOnClickListener(v -> NavigationUI.onNavDestinationSelected(
                requireActivity().<com.google.android.material.bottomnavigation.BottomNavigationView>findViewById(R.id.bottom_nav)
                        .getMenu().findItem(R.id.scanFragment), navController));

        viewModel.sessions().observe(getViewLifecycleOwner(), sessions -> {
            adapter.submitList(new ArrayList<>(sessions.values()));
            binding.emptyCard.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
