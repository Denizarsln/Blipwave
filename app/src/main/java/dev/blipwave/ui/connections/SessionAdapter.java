package dev.blipwave.ui.connections;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import dev.blipwave.R;
import dev.blipwave.data.DeviceSession;
import dev.blipwave.data.RssiHistory;
import dev.blipwave.databinding.ItemSessionBinding;
import dev.blipwave.util.Cancellable;

/** Açık oturumlar. Her kart kendi oturumunun durumunu ve RSSI'ını canlı izler. */
public final class SessionAdapter extends ListAdapter<DeviceSession, SessionAdapter.Holder> {

    public interface Actions {
        void onOpen(DeviceSession session);

        void onForget(DeviceSession session);
    }

    private final Actions actions;

    public SessionAdapter(Actions actions) {
        super(DIFF);
        this.actions = actions;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.unbind();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull Holder holder) {
        holder.unbind();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull Holder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) holder.bind(getItem(position));
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemSessionBinding binding;
        private Cancellable stateSubscription = Cancellable.NONE;
        private Cancellable rssiSubscription = Cancellable.NONE;

        Holder(ItemSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            View.OnClickListener open = v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) actions.onOpen(getItem(position));
            };
            binding.getRoot().setOnClickListener(open);
            binding.open.setOnClickListener(open);
            binding.forget.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) actions.onForget(getItem(position));
            });
        }

        void bind(DeviceSession session) {
            unbind();
            String name = session.connection().getDeviceName();
            binding.name.setText(name != null ? name : itemView.getContext().getString(R.string.device_unnamed));
            binding.address.setText(session.connection().getAddress());
            stateSubscription = session.connection().state().observe(binding.statusPill::setState);
            rssiSubscription = session.rssiHistory().observe(this::renderRssi);
        }

        private void renderRssi(List<RssiHistory.Sample> history) {
            if (history.isEmpty()) {
                binding.signalBars.setVisibility(View.INVISIBLE);
                return;
            }
            binding.signalBars.setVisibility(View.VISIBLE);
            binding.signalBars.setRssi(history.get(history.size() - 1).rssi);
        }

        void unbind() {
            stateSubscription.cancel();
            rssiSubscription.cancel();
            stateSubscription = Cancellable.NONE;
            rssiSubscription = Cancellable.NONE;
        }
    }

    private static final DiffUtil.ItemCallback<DeviceSession> DIFF = new DiffUtil.ItemCallback<DeviceSession>() {
        @Override
        public boolean areItemsTheSame(@NonNull DeviceSession oldItem, @NonNull DeviceSession newItem) {
            return oldItem.connection().getAddress().equals(newItem.connection().getAddress());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DeviceSession oldItem, @NonNull DeviceSession newItem) {
            return oldItem == newItem;
        }
    };
}
