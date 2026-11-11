package dev.blipwave.ui.chat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import dev.blipwave.R;
import dev.blipwave.data.ChatMessage;
import dev.blipwave.data.PayloadCodec;

/** Sohbet baloncukları: TX sağda gradyanlı, RX solda camgöbeği kenarlıklı. */
public final class MessageAdapter extends ListAdapter<ChatMessage, MessageAdapter.Holder> {

    private static final int TYPE_TX = 0;
    private static final int TYPE_RX = 1;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    /** Giriş animasyonu yalnızca yeni mesajlarda oynasın. */
    private final Set<Long> appeared = new HashSet<>();
    private PayloadCodec.Format format = PayloadCodec.Format.TEXT;

    public MessageAdapter() {
        super(DIFF);
    }

    /** Mod değişince tüm döküm yeni gösterimle yeniden çizilir. */
    @SuppressLint("NotifyDataSetChanged")
    public void setFormat(PayloadCodec.Format value) {
        if (format == value) return;
        format = value;
        notifyDataSetChanged();
    }

    /** Ekran açıldığında zaten var olan mesajlar animasyonsuz gelsin. */
    public void markSeen(java.util.List<ChatMessage> messages) {
        for (ChatMessage message : messages) appeared.add(message.id);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).direction == ChatMessage.Direction.TX ? TYPE_TX : TYPE_RX;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_TX ? R.layout.item_message_tx : R.layout.item_message_rx;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false), viewType == TYPE_TX);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatMessage message = getItem(position);
        holder.bind(message);
        boolean isNew = appeared.add(message.id);
        if (isNew) holder.playEntrance();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final TextView bubble;
        private final TextView meta;
        private final boolean tx;

        Holder(View view, boolean tx) {
            super(view);
            this.tx = tx;
            bubble = view.findViewById(R.id.bubble);
            meta = view.findViewById(R.id.meta);
        }

        void bind(ChatMessage message) {
            // Geri dönüştürülen baloncukta yarım kalmış animasyon değerlerini temizle
            bubble.animate().cancel();
            bubble.setAlpha(1f);
            bubble.setScaleX(1f);
            bubble.setScaleY(1f);
            Context context = itemView.getContext();
            bubble.setText(PayloadCodec.decode(message.payload(), format));
            bubble.setTypeface(format == PayloadCodec.Format.HEX ? Typeface.MONOSPACE : Typeface.DEFAULT);
            meta.setText(context.getString(tx ? R.string.chat_tx : R.string.chat_rx)
                    + " · " + timeFormat.format(new Date(message.timestampMillis))
                    + " · " + context.getString(R.string.chat_bytes, (long) message.size()));
        }

        void playEntrance() {
            // Baloncuk kendi tarafındaki alt köşeden büyür
            bubble.post(() -> {
                bubble.setPivotX(tx ? bubble.getWidth() : 0f);
                bubble.setPivotY(bubble.getHeight());
                bubble.setAlpha(0f);
                bubble.setScaleX(0.8f);
                bubble.setScaleY(0.8f);
                bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
            });
        }
    }

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF = new DiffUtil.ItemCallback<ChatMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            // Mesajlar değişmez; aynı kimlik aynı içerik demektir
            return oldItem.id == newItem.id;
        }
    };
}
