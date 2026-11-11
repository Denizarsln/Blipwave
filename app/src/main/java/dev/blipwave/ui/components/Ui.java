package dev.blipwave.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import dev.blipwave.R;
import dev.blipwave.ble.SignalLevel;
import dev.blipwave.ble.model.ConnectionState;

/** Görünümler arasında paylaşılan küçük yardımcılar. */
public final class Ui {

    private Ui() { }

    public static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    @ColorInt
    public static int color(Context context, @ColorRes int res) {
        return ContextCompat.getColor(context, res);
    }

    @ColorInt
    public static int signalColor(Context context, SignalLevel level) {
        switch (level) {
            case EXCELLENT: return color(context, R.color.bp_signal_excellent);
            case GOOD: return color(context, R.color.bp_signal_good);
            case FAIR: return color(context, R.color.bp_signal_fair);
            case WEAK:
            default: return color(context, R.color.bp_signal_weak);
        }
    }

    @ColorInt
    public static int statusColor(Context context, ConnectionState state) {
        switch (state.status) {
            case CONNECTING: return color(context, R.color.bp_signal_fair);
            case CONNECTED: return color(context, R.color.bp_signal_excellent);
            case DISCONNECTED:
                return state.unexpected ? color(context, R.color.bp_signal_weak) : color(context, R.color.bp_on_surface_variant);
            case IDLE:
            default: return color(context, R.color.bp_on_surface_variant);
        }
    }

    @StringRes
    public static int statusLabel(ConnectionState state) {
        switch (state.status) {
            case CONNECTING: return R.string.status_connecting;
            case CONNECTED: return R.string.status_connected;
            case DISCONNECTED: return state.unexpected ? R.string.status_lost : R.string.status_disconnected;
            case IDLE:
            default: return R.string.status_idle;
        }
    }

    /** Klasik Bluetooth cihaz sınıfının okunabilir adı; bilinmiyorsa 0. */
    @StringRes
    public static int categoryLabel(dev.blipwave.ble.model.ScannedDevice.Category category) {
        switch (category) {
            case AUDIO_VIDEO: return R.string.category_audio_video;
            case PHONE: return R.string.category_phone;
            case COMPUTER: return R.string.category_computer;
            case WEARABLE: return R.string.category_wearable;
            case PERIPHERAL: return R.string.category_peripheral;
            case HEALTH: return R.string.category_health;
            case IMAGING: return R.string.category_imaging;
            case NETWORKING: return R.string.category_networking;
            case TOY: return R.string.category_toy;
            case UNKNOWN:
            default: return 0;
        }
    }

    /** Başlık yazısını marka gradyanıyla (mor → pembe → mercan) boyar. */
    public static void applyBrandGradient(TextView view) {
        Context context = view.getContext();
        int[] colors = {
                color(context, R.color.electric_violet),
                color(context, R.color.hot_pink),
                color(context, R.color.coral),
        };
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            float width = view.getPaint().measureText(view.getText().toString());
            view.getPaint().setShader(new LinearGradient(0, 0, Math.max(width, 1f), 0, colors, null, Shader.TileMode.CLAMP));
            view.invalidate();
        });
    }

    /** Küçük renkli etiket (DEMO, UART, özellik rozetleri). */
    public static TextView badge(Context context, String text, @ColorInt int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setAllCaps(false);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        int horizontal = (int) dp(context, 8);
        int vertical = (int) dp(context, 3);
        view.setPadding(horizontal, vertical, horizontal, vertical);
        styleBadge(view, color);
        return view;
    }

    public static void styleBadge(TextView view, @ColorInt int color) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(view.getContext(), 999));
        background.setColor(ColorUtils.setAlphaComponent(color, 36));
        background.setStroke((int) dp(view.getContext(), 1), ColorUtils.setAlphaComponent(color, 128));
        view.setBackground(background);
        view.setTextColor(color);
    }

    public static ViewGroup.MarginLayoutParams wrapWithEndMargin(Context context, float marginDp) {
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd((int) dp(context, marginDp));
        params.bottomMargin = (int) dp(context, 6);
        return params;
    }

    /** Devre dışı görünümü yarı saydam yapar; TextView tabanlı butonlar kendiliğinden soluklaşmaz. */
    public static void setEnabledWithAlpha(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.4f);
    }

    public static ColorStateList tint(@ColorInt int color) {
        return ColorStateList.valueOf(color);
    }
}
