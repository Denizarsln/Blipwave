package dev.blipwave.ui.scan;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.blipwave.R;
import dev.blipwave.ble.NordicUart;
import dev.blipwave.ble.SignalLevel;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.databinding.ItemDeviceBinding;
import dev.blipwave.ui.components.Ui;

/**
 * Tarama sonuçları. Kart ilk göründüğünde aşağıdan yaylanarak gelir;
 * dokununca genişleyip ayrıntıları ve "Bağlan" butonunu gösterir.
 */
public final class DeviceAdapter extends ListAdapter<ScannedDevice, DeviceAdapter.Holder> {

    public interface ConnectListener {
        void onConnect(ScannedDevice device);
    }

    private static final Object PAYLOAD_EXPANSION = new Object();

    private final ConnectListener connectListener;
    /** Giriş animasyonu bir kez oynasın diye görülen adresler. */
    private final Set<String> appeared = new HashSet<>();
    @Nullable private String expandedAddress;

    public DeviceAdapter(ConnectListener connectListener) {
        super(DIFF);
        this.connectListener = connectListener;
    }

    @Nullable
    public String getExpandedAddress() {
        return expandedAddress;
    }

    public void setExpandedAddress(@Nullable String address) {
        expandedAddress = address;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ScannedDevice device = getItem(position);
        holder.resetAnimation();
        holder.bind(device);
        holder.bindExpansion(Objects.equals(device.address, expandedAddress), false);
        if (appeared.add(device.address)) holder.playEntrance();
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        // Yarıda kalan giriş animasyonu geri dönüştürülen karta taşınmasın
        holder.resetAnimation();
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_EXPANSION)) {
            holder.bindExpansion(Objects.equals(getItem(position).address, expandedAddress), true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void toggle(int position) {
        String address = getItem(position).address;
        String previous = expandedAddress;
        expandedAddress = Objects.equals(previous, address) ? null : address;
        // RecyclerView üzerinde TransitionManager kullanılmaz (çocuk görünüm çakışmasıyla çöker);
        // diğer kartlar RecyclerView'ın kendi taşıma animasyonuyla kayar
        notifyItemChanged(position, PAYLOAD_EXPANSION);
        if (previous != null && !previous.equals(address)) {
            List<ScannedDevice> items = getCurrentList();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).address.equals(previous)) notifyItemChanged(i, PAYLOAD_EXPANSION);
            }
        }
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemDeviceBinding binding;

        Holder(ItemDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.card.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) toggle(position);
            });
            binding.connect.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) connectListener.onConnect(getItem(position));
            });
        }

        void bind(ScannedDevice device) {
            Context context = itemView.getContext();
            boolean named = device.hasName();
            binding.name.setText(named ? device.name : context.getString(R.string.device_unnamed));
            binding.name.setTypeface(null, named ? Typeface.BOLD : Typeface.BOLD_ITALIC);
            binding.name.setTextColor(Ui.color(context, named ? R.color.bp_on_background : R.color.bp_on_surface_variant));
            // Klasik/çift mod cihazlar kapalı kartta da bir bakışta ayırt edilsin
            String transportLabel = transportLabel(context, device.transport);
            binding.address.setText(device.address);
            binding.transportTag.setVisibility(transportLabel == null ? View.GONE : View.VISIBLE);
            if (transportLabel != null) {
                binding.transportTag.setText(transportLabel);
                binding.transportTag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
                int padH = (int) Ui.dp(context, 6);
                int padV = (int) Ui.dp(context, 1);
                binding.transportTag.setPadding(padH, padV, padH, padV);
                Ui.styleBadge(binding.transportTag, Ui.color(context, R.color.sunset_orange));
            }

            int avatar;
            if (device.isClassic()) avatar = R.drawable.bg_circle_warm;
            else if (named) avatar = R.drawable.bg_circle_cool;
            else avatar = R.drawable.bg_circle_muted;
            binding.avatar.setBackgroundResource(avatar);
            binding.avatarIcon.setImageTintList(Ui.tint(avatar != R.drawable.bg_circle_muted
                    ? android.graphics.Color.WHITE
                    : Ui.color(context, R.color.bp_on_surface_variant)));

            binding.signalBars.setRssi(device.rssi);
            binding.rssi.setText(context.getString(R.string.rssi_value, device.rssi));
            binding.rssi.setTextColor(Ui.signalColor(context, SignalLevel.fromRssi(device.rssi)));

            binding.badges.removeAllViews();
            int category = Ui.categoryLabel(device.category);
            if (category != 0) addBadge(context, context.getString(category), Ui.color(context, R.color.bp_secondary));
            // Çift modda satır taşmasın diye bağlanılabilirlik rozeti atlanır
            if (device.transport == ScannedDevice.Transport.LE) {
                addBadge(context,
                        context.getString(device.connectable ? R.string.device_connectable : R.string.device_not_connectable),
                        Ui.color(context, device.connectable ? R.color.bp_signal_excellent : R.color.bp_on_surface_variant));
            }
            if (device.serviceUuids.contains(NordicUart.SERVICE)) {
                addBadge(context, context.getString(R.string.device_uart_badge), Ui.color(context, R.color.bp_signal_fair));
            }
            if (!device.serviceUuids.isEmpty()) {
                addBadge(context, context.getString(R.string.device_services_count, device.serviceUuids.size()),
                        Ui.color(context, R.color.bp_primary));
            }

            // Yalnız klasik cihazlar seri port (SPP) ile bağlanır; kullanıcıya bunu ve olası eşleştirmeyi anlat
            boolean classicOnly = device.transport == ScannedDevice.Transport.CLASSIC;
            binding.classicNote.setVisibility(classicOnly ? View.VISIBLE : View.GONE);
            Ui.setEnabledWithAlpha(binding.connect, device.canConnect());
        }

        private void addBadge(Context context, String text, int color) {
            binding.badges.addView(Ui.badge(context, text, color), Ui.wrapWithEndMargin(context, 8));
        }

        @Nullable
        private String transportLabel(Context context, ScannedDevice.Transport transport) {
            switch (transport) {
                case CLASSIC: return context.getString(R.string.transport_classic);
                case DUAL: return context.getString(R.string.transport_dual);
                case LE:
                default: return null;
            }
        }

        void bindExpansion(boolean expanded, boolean animate) {
            binding.details.animate().cancel();
            binding.details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            float rotation = expanded ? 180f : 0f;
            if (animate) {
                binding.chevron.animate().rotation(rotation).setDuration(220L).start();
                if (expanded) {
                    binding.details.setAlpha(0f);
                    binding.details.setTranslationY(-Ui.dp(itemView.getContext(), 8));
                    binding.details.animate().alpha(1f).translationY(0f).setDuration(260L).start();
                }
            } else {
                binding.chevron.setRotation(rotation);
                binding.details.setAlpha(1f);
                binding.details.setTranslationY(0f);
            }
        }

        void resetAnimation() {
            View card = binding.card;
            card.animate().cancel();
            card.setAlpha(1f);
            card.setTranslationY(0f);
            card.setScaleX(1f);
            card.setScaleY(1f);
        }

        void playEntrance() {
            // itemView'a dokunma: onu RecyclerView'ın kendi animatörü yönetir
            View view = binding.card;
            view.setAlpha(0f);
            view.setTranslationY(Ui.dp(view.getContext(), 32));
            view.setScaleX(0.94f);
            view.setScaleY(0.94f);
            // Ölçek 1'i aşmasın diye yaylanma yerine yumuşak yavaşlama
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(360L)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
    }

    private static final DiffUtil.ItemCallback<ScannedDevice> DIFF = new DiffUtil.ItemCallback<ScannedDevice>() {
        @Override
        public boolean areItemsTheSame(@NonNull ScannedDevice oldItem, @NonNull ScannedDevice newItem) {
            return oldItem.address.equals(newItem.address);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ScannedDevice oldItem, @NonNull ScannedDevice newItem) {
            return oldItem.equals(newItem);
        }
    };
}
