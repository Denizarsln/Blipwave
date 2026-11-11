package dev.blipwave.ui.components;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;

import dev.blipwave.ble.model.ConnectionState;

/** Bağlanıyor / Bağlandı / Koptu durumunu renk, nokta ve metinle gösteren hap. */
public class StatusPillView extends AppCompatTextView {

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GradientDrawable background = new GradientDrawable();
    private final ValueAnimator blink = ValueAnimator.ofFloat(1f, 0.25f);
    private final float dotRadius;
    private final float dotInset;

    private ConnectionState state = ConnectionState.IDLE;
    private int color;
    private ValueAnimator colorAnimator;

    public StatusPillView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dotRadius = Ui.dp(context, 4);
        dotInset = Ui.dp(context, 12);
        setGravity(Gravity.CENTER_VERTICAL);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        setPaddingRelative((int) (dotInset + dotRadius * 2 + Ui.dp(context, 8)), (int) Ui.dp(context, 6),
                (int) Ui.dp(context, 12), (int) Ui.dp(context, 6));
        background.setCornerRadius(Ui.dp(context, 999));
        setBackground(background);

        blink.setDuration(600L);
        blink.setRepeatMode(ValueAnimator.REVERSE);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.addUpdateListener(animation -> invalidate());

        color = Ui.statusColor(context, state);
        applyColor(color);
        setText(Ui.statusLabel(state));
    }

    public void setState(ConnectionState newState) {
        if (newState.equals(state)) return;
        state = newState;
        int target = Ui.statusColor(getContext(), newState);
        setText(Ui.statusLabel(newState));
        // Metin değişimini hafif bir "zıplama" ile vurgula
        setAlpha(0f);
        setTranslationY(Ui.dp(getContext(), 4));
        animate().alpha(1f).translationY(0f).setDuration(220L).start();

        if (colorAnimator != null) colorAnimator.cancel();
        colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), color, target);
        colorAnimator.setDuration(400L);
        colorAnimator.addUpdateListener(animation -> applyColor((int) animation.getAnimatedValue()));
        colorAnimator.start();
        updateBlink();
    }

    private void applyColor(int value) {
        color = value;
        background.setColor(ColorUtils.setAlphaComponent(value, 36));
        background.setStroke((int) Ui.dp(getContext(), 1), ColorUtils.setAlphaComponent(value, 140));
        setTextColor(value);
        invalidate();
    }

    private void updateBlink() {
        if (state.status == ConnectionState.Status.CONNECTING && isAttachedToWindow()) {
            if (!blink.isStarted()) blink.start();
        } else {
            blink.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateBlink();
    }

    @Override
    protected void onDetachedFromWindow() {
        blink.cancel();
        if (colorAnimator != null) colorAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float alpha = blink.isRunning() ? (float) blink.getAnimatedValue() : 1f;
        dotPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * alpha)));
        float cx = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - dotInset - dotRadius : dotInset + dotRadius;
        canvas.drawCircle(cx, getHeight() / 2f, dotRadius, dotPaint);
        super.onDraw(canvas);
    }
}
