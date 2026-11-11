package dev.blipwave.ui.device;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dagger.hilt.android.AndroidEntryPoint;
import dev.blipwave.R;
import dev.blipwave.ble.GattUuids;
import dev.blipwave.ble.NordicUart;
import dev.blipwave.ble.SignalLevel;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.data.RssiHistory;
import dev.blipwave.databinding.FragmentDeviceDetailBinding;
import dev.blipwave.databinding.ItemCharacteristicBinding;
import dev.blipwave.databinding.ItemServiceBinding;
import dev.blipwave.ui.chat.ChatFragment;
import dev.blipwave.ui.components.Ui;

@AndroidEntryPoint
public class DeviceDetailFragment extends Fragment {

    public static final String ARG_ADDRESS = "address";
    public static final String ARG_NAME = "name";
    public static final String ARG_CLASSIC = "classic";
    private static final String STATE_EXPANDED = "expanded_services";

    private FragmentDeviceDetailBinding binding;
    private DeviceDetailViewModel viewModel;
    private ConnectionState connectionState = ConnectionState.IDLE;
    private List<GattServiceInfo> services = Collections.emptyList();
    /** Açık servis kartları; UART servisi varsayılan olarak açık gelir. */
    private final Set<String> expandedServices = new HashSet<>();
    private boolean expandedRestored;

    public static Bundle args(String address, @Nullable String name) {
        return args(address, name, false);
    }

    /** @param classic klasik Bluetooth cihazı; seri port (SPP) ile bağlanılır */
    public static Bundle args(String address, @Nullable String name, boolean classic) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_ADDRESS, address);
        bundle.putString(ARG_NAME, name);
        bundle.putBoolean(ARG_CLASSIC, classic);
        return bundle;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDeviceDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(DeviceDetailViewModel.class);
        if (savedInstanceState != null && savedInstanceState.getStringArrayList(STATE_EXPANDED) != null) {
            expandedServices.addAll(savedInstanceState.getStringArrayList(STATE_EXPANDED));
            expandedRestored = true;
        }

        String name = viewModel.displayName();
        binding.toolbar.setTitle(name != null ? name : getString(R.string.device_unnamed));
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.address.setText(viewModel.address());
        Ui.styleBadge(binding.servicesCount, Ui.color(requireContext(), R.color.bp_primary));

        binding.disconnectButton.setOnClickListener(v -> viewModel.disconnect());
        binding.reconnectButton.setOnClickListener(v -> viewModel.connect());
        binding.consoleButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(ChatFragment.ARG_ADDRESS, viewModel.address());
            NavHostFragment.findNavController(this).navigate(R.id.action_device_to_chat, args);
        });

        viewModel.state().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.services().observe(getViewLifecycleOwner(), value -> {
            services = value;
            renderServices();
            renderButtons();
            renderSignal(viewModel.rssiHistory().getValue());
        });
        viewModel.rssiHistory().observe(getViewLifecycleOwner(), this::renderSignal);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_EXPANDED, new ArrayList<>(expandedServices));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /** Konsol açılabilir mi: BLE'de Nordic UART servisi, klasikte seri port (SPP). */
    private boolean hasUart() {
        return GattUuids.supportsConsole(services);
    }

    private void renderState(ConnectionState state) {
        connectionState = state;
        binding.orb.setState(state);
        binding.statusPill.setState(state);
        boolean showReason = state.status == ConnectionState.Status.DISCONNECTED && state.reason != null;
        binding.reason.setVisibility(showReason ? View.VISIBLE : View.GONE);
        if (showReason) binding.reason.setText(getString(R.string.detail_reason, state.reason));
        renderButtons();
        renderServices();
        // Grafik yalnızca bağlıyken akar
        renderSignal(viewModel.rssiHistory().getValue());
    }

    private void renderButtons() {
        boolean active = connectionState.isActive();
        binding.disconnectButton.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.reconnectButton.setVisibility(active ? View.GONE : View.VISIBLE);
        Ui.setEnabledWithAlpha(binding.consoleButton, connectionState.isConnected() && hasUart());
        binding.noUart.setVisibility(connectionState.isConnected() && !hasUart() ? View.VISIBLE : View.GONE);
    }

    private void renderSignal(@Nullable List<RssiHistory.Sample> history) {
        if (binding == null) return;
        // Klasik seri port bağlantısında RSSI okunamaz; boş grafik göstermek yerine kartı gizle
        binding.signalCard.setVisibility(GattUuids.isSerialPort(services) ? View.GONE : View.VISIBLE);
        List<RssiHistory.Sample> samples = history != null ? history : Collections.emptyList();
        binding.chart.setSamples(samples, connectionState.isConnected());
        binding.signalWaiting.setVisibility(samples.isEmpty() ? View.VISIBLE : View.GONE);
        if (samples.isEmpty()) {
            binding.latestRssi.setVisibility(View.GONE);
            binding.latestBars.setVisibility(View.GONE);
            return;
        }
        int latest = samples.get(samples.size() - 1).rssi;
        binding.latestRssi.setVisibility(View.VISIBLE);
        binding.latestBars.setVisibility(View.VISIBLE);
        binding.latestRssi.setText(getString(R.string.rssi_value, latest));
        binding.latestRssi.setTextColor(Ui.signalColor(requireContext(), SignalLevel.fromRssi(latest)));
        binding.latestBars.setRssi(latest);
    }

    private void renderServices() {
        if (binding == null) return;
        Context context = requireContext();
        boolean empty = services.isEmpty();
        boolean connecting = connectionState.status == ConnectionState.Status.CONNECTING;

        binding.servicesCount.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.servicesCount.setText(String.valueOf(services.size()));
        binding.servicesProgress.setVisibility(empty && connecting ? View.VISIBLE : View.GONE);
        binding.servicesMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.servicesMessage.setText(connecting ? R.string.detail_discovering : R.string.detail_services_empty);

        if (!expandedRestored && services.size() > 0 && GattUuids.supportsConsole(services)) {
            expandedServices.add(NordicUart.SERVICE.toString());
            expandedRestored = true;
        }

        binding.servicesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (GattServiceInfo service : services) {
            ItemServiceBinding card = ItemServiceBinding.inflate(inflater, binding.servicesContainer, false);
            String serviceName = GattUuids.serviceName(service.uuid);
            card.serviceName.setText(serviceName != null ? serviceName : getString(R.string.service_unknown));
            card.serviceUuid.setText(GattUuids.shortLabel(service.uuid));
            card.characteristicCount.setText(getString(R.string.detail_characteristics, service.characteristics.size()));

            for (GattServiceInfo.Characteristic characteristic : service.characteristics) {
                card.characteristics.addView(characteristicRow(inflater, card, characteristic));
            }

            String key = service.uuid.toString();
            boolean expanded = expandedServices.contains(key);
            card.characteristics.setVisibility(expanded ? View.VISIBLE : View.GONE);
            card.chevron.setRotation(expanded ? 180f : 0f);
            card.getRoot().setOnClickListener(v -> {
                boolean nowExpanded = !expandedServices.remove(key);
                if (nowExpanded) expandedServices.add(key);
                card.characteristics.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
                card.chevron.animate().rotation(nowExpanded ? 180f : 0f).setDuration(220L).start();
            });
            binding.servicesContainer.addView(card.getRoot());
        }
    }

    private View characteristicRow(LayoutInflater inflater, ItemServiceBinding card,
                                   GattServiceInfo.Characteristic characteristic) {
        Context context = requireContext();
        ItemCharacteristicBinding row = ItemCharacteristicBinding.inflate(inflater, card.characteristics, false);
        UUID uuid = characteristic.uuid;
        String name = GattUuids.characteristicName(uuid);
        row.characteristicName.setText(name != null ? name : getString(R.string.characteristic_unknown));
        row.characteristicUuid.setText(GattUuids.shortLabel(uuid));
        for (GattServiceInfo.Property property : characteristic.properties) {
            int color;
            switch (property) {
                case READ: color = Ui.color(context, R.color.bp_signal_excellent); break;
                case WRITE:
                case WRITE_NO_RESPONSE: color = Ui.color(context, R.color.bp_signal_fair); break;
                case NOTIFY:
                case INDICATE:
                default: color = Ui.color(context, R.color.bp_signal_good); break;
            }
            row.propertyBadges.addView(Ui.badge(context, property.name().replace('_', ' '), color),
                    Ui.wrapWithEndMargin(context, 6));
        }
        return row.getRoot();
    }
}
