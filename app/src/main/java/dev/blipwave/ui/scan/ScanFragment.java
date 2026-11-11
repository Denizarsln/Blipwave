package dev.blipwave.ui.scan;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import dagger.hilt.android.AndroidEntryPoint;
import dev.blipwave.R;
import dev.blipwave.ble.ScanFilter;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.databinding.FragmentScanBinding;
import dev.blipwave.ui.components.Ui;
import dev.blipwave.ui.device.DeviceDetailFragment;
import dev.blipwave.ui.permissions.BlePermissions;

@AndroidEntryPoint
public class ScanFragment extends Fragment {

    private static final String STATE_EXPANDED = "expanded";
    private static final String STATE_FILTERS_OPEN = "filters_open";
    private static final String STATE_DENIED = "denied_once";

    private FragmentScanBinding binding;
    private ScanViewModel viewModel;
    private DeviceAdapter adapter;
    private boolean filtersOpen;
    private boolean deniedOnce;
    @Nullable private ScanUiState lastState;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = !result.isEmpty();
                for (Boolean granted : result.values()) allGranted &= granted;
                if (!allGranted) deniedOnce = true;
                renderPermission();
            });

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> viewModel.startScan());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentScanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        if (savedInstanceState != null) {
            filtersOpen = savedInstanceState.getBoolean(STATE_FILTERS_OPEN);
            deniedOnce = savedInstanceState.getBoolean(STATE_DENIED);
        }

        Context context = requireContext();
        Ui.applyBrandGradient(binding.title);
        Ui.styleBadge(binding.demoBadge, Ui.color(context, R.color.bp_signal_fair));

        adapter = new DeviceAdapter(this::openDevice);
        if (savedInstanceState != null) adapter.setExpandedAddress(savedInstanceState.getString(STATE_EXPANDED));
        binding.deviceList.setLayoutManager(new LinearLayoutManager(context));
        binding.deviceList.setAdapter(adapter);
        // RSSI her güncellendiğinde "değişti" çapraz geçişi kartları hayalet gibi çiftliyordu
        RecyclerView.ItemAnimator animator = binding.deviceList.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);

        binding.scanToggle.setOnClickListener(v -> viewModel.toggleScan());
        binding.filterNamed.setOnCheckedChangeListener((button, checked) -> viewModel.setNamedOnly(checked));
        binding.filterToggle.setOnClickListener(v -> {
            filtersOpen = !filtersOpen;
            renderFilterPanel();
        });
        binding.minSignalSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) viewModel.setMinRssi(Math.round(value));
        });
        binding.sortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.sort_name) viewModel.setSortOrder(ScanFilter.SortOrder.NAME);
            else if (id == R.id.sort_recent) viewModel.setSortOrder(ScanFilter.SortOrder.RECENT);
            else viewModel.setSortOrder(ScanFilter.SortOrder.SIGNAL);
        });
        binding.transportGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.transport_le) viewModel.setTransportFilter(ScanFilter.TransportFilter.LE);
            else if (id == R.id.transport_classic) viewModel.setTransportFilter(ScanFilter.TransportFilter.CLASSIC);
            else viewModel.setTransportFilter(ScanFilter.TransportFilter.ALL);
        });

        binding.permissionGrant.setOnClickListener(v -> {
            if (deniedOnce && !BlePermissions.canAskAgain(this)) {
                startActivity(BlePermissions.appSettingsIntent(context));
            } else {
                permissionLauncher.launch(BlePermissions.required());
            }
        });
        binding.permissionDemo.setOnClickListener(v -> viewModel.enableDemoMode());

        renderFilterPanel();
        viewModel.uiState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Kullanıcı sistem ayarlarından dönünce izin durumunu tazele
        renderPermission();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Ekran görünmüyorken tarama pil tüketmesin
        if (viewModel != null) viewModel.stopScan();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FILTERS_OPEN, filtersOpen);
        outState.putBoolean(STATE_DENIED, deniedOnce);
        if (adapter != null) outState.putString(STATE_EXPANDED, adapter.getExpandedAddress());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void openDevice(ScannedDevice device) {
        // Bağlanmadan önce taramayı durdurmak bağlantı kurulumunu hızlandırır
        viewModel.stopScan();
        NavHostFragment.findNavController(this).navigate(R.id.action_global_device,
                DeviceDetailFragment.args(device.address, device.name,
                        device.transport == dev.blipwave.ble.model.ScannedDevice.Transport.CLASSIC));
    }

    private void render(ScanUiState state) {
        lastState = state;
        Context context = requireContext();

        binding.demoBadge.setVisibility(state.demo ? View.VISIBLE : View.GONE);
        renderPermission();

        binding.radar.setActive(state.scanning);
        binding.radar.setBlipCount(state.totalCount);
        binding.scanStatus.setText(state.scanning ? R.string.scan_status_scanning : R.string.scan_status_idle);
        binding.scanToggle.setText(state.scanning ? R.string.scan_stop : R.string.scan_start);
        binding.scanToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                state.scanning ? R.drawable.ic_stop : R.drawable.ic_play, 0, 0, 0);
        binding.scanToggle.setBackgroundResource(state.scanning ? R.drawable.btn_warm : R.drawable.btn_brand);

        binding.scanCount.setText(getString(R.string.scan_count, state.devices.size(), state.totalCount));
        if (binding.filterNamed.isChecked() != state.filter.namedOnly) binding.filterNamed.setChecked(state.filter.namedOnly);
        binding.minSignalLabel.setText(getString(R.string.filter_min_signal, state.filter.minRssi));
        if (Math.round(binding.minSignalSlider.getValue()) != state.filter.minRssi) {
            binding.minSignalSlider.setValue(state.filter.minRssi);
        }

        renderError(state.error);
        adapter.submitList(state.devices);

        boolean empty = state.devices.isEmpty() && state.error == null;
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            int message;
            if (state.totalCount > 0) message = R.string.scan_empty_filtered;
            else if (state.scanning) message = R.string.scan_searching;
            else message = R.string.scan_empty_body;
            binding.emptyText.setText(message);
        }
        binding.filterToggle.setImageTintList(Ui.tint(Ui.color(context,
                filtersOpen ? R.color.bp_primary : R.color.bp_on_surface_variant)));
    }

    private void renderPermission() {
        if (binding == null) return;
        boolean demo = lastState != null && lastState.demo;
        // Demo modunda donanım izni gerekmez
        boolean needsPermission = !demo && !BlePermissions.granted(requireContext());
        binding.permissionCard.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
        binding.radarCard.setVisibility(needsPermission ? View.GONE : View.VISIBLE);
        binding.filterBar.setVisibility(needsPermission ? View.GONE : View.VISIBLE);
        binding.deviceList.setVisibility(needsPermission ? View.GONE : View.VISIBLE);
        if (needsPermission) {
            binding.filterPanel.setVisibility(View.GONE);
            binding.emptyText.setVisibility(View.GONE);
            binding.errorCard.setVisibility(View.GONE);
        } else {
            renderFilterPanel();
        }

        boolean permanentlyDenied = deniedOnce && !BlePermissions.canAskAgain(this);
        int body;
        if (deniedOnce) body = R.string.permission_denied_body;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) body = R.string.permission_body_s;
        else body = R.string.permission_body_legacy;
        binding.permissionBody.setText(body);
        binding.permissionGrant.setText(permanentlyDenied ? R.string.permission_open_settings : R.string.permission_grant);
    }

    private void renderFilterPanel() {
        if (binding == null) return;
        boolean permissionVisible = binding.permissionCard.getVisibility() == View.VISIBLE;
        binding.filterPanel.setVisibility(filtersOpen && !permissionVisible ? View.VISIBLE : View.GONE);
        binding.filterToggle.setImageTintList(Ui.tint(Ui.color(requireContext(),
                filtersOpen ? R.color.bp_primary : R.color.bp_on_surface_variant)));
    }

    private void renderError(@Nullable BleError error) {
        boolean permissionVisible = binding.permissionCard.getVisibility() == View.VISIBLE;
        binding.errorCard.setVisibility(error != null && !permissionVisible ? View.VISIBLE : View.GONE);
        if (error == null) return;
        switch (error.type) {
            case BLUETOOTH_OFF:
                binding.errorText.setText(R.string.error_bluetooth_off);
                break;
            case UNSUPPORTED:
                binding.errorText.setText(R.string.error_unsupported);
                break;
            case PERMISSION_DENIED:
                binding.errorText.setText(R.string.error_permission);
                break;
            case SCAN_FAILED:
                binding.errorText.setText(getString(R.string.error_scan_failed, error.code));
                break;
            default:
                binding.errorText.setText(error.getMessage());
                break;
        }
        if (error.type == BleError.Type.BLUETOOTH_OFF) {
            binding.errorAction.setText(R.string.action_turn_on);
            binding.errorAction.setOnClickListener(v -> {
                viewModel.dismissError();
                try {
                    enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Bazı cihazlarda bu ekran yoktur; kullanıcı Bluetooth'u elle açar
                }
            });
        } else {
            binding.errorAction.setText(R.string.action_try_demo);
            binding.errorAction.setOnClickListener(v -> viewModel.enableDemoMode());
        }
    }
}
