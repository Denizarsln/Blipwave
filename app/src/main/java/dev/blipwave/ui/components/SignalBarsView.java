package dev.blipwave.ui.components;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import dev.blipwave.R;
import dev.blipwave.ble.SignalLevel;

/** Dört çubuklu sinyal gücü göstergesi; renk ve dolu çubuk sayısı RSSI'a göre canlanır. */
public class SignalBarsView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int idleColor;
    private final float barWidth;
    private final float gap;
    private final float corner;

    private int activeColor;
    private int bars;
    private ValueAnimator colorAnimator;

    public SignalBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        idleColor = ColorUtils.setAlphaComponent(Ui.color(context, R.color.bp_outline), 200);
        activeColor = Ui.color(context, R.color.bp_signal_excellent);
        barWidth = Ui.dp(context, 5);
        gap = Ui.dp(context, 3);
        corner = Ui.dp(context, 2);
    }

    public void setRssi(int rssi) {
        SignalLevel level = SignalLevel.fromRssi(rssi);
        int target = Ui.signalColor(getContext(), level);
        bars = level.bars;
        if (target == activeColor) {
            invalidate();
            return;
        }
        if (colorAnimator != null) colorAnimator.cancel();
        colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), activeColor, target);
        colorAnimator.setDuration(400L);
        colorAnimator.addUpdateListener(animation -> {
            activeColor = (int) animation.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) (barWidth * 4 + gap * 3) + getPaddingLeft() + getPaddingRight();
        int height = (int) Ui.dp(getContext(), 20) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (colorAnimator != null) colorAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float bottom = getHeight() - getPaddingBottom();
        float maxHeight = bottom - getPaddingTop();
        for (int index = 1; index <= 4; index++) {
            float left = getPaddingLeft() + (index - 1) * (barWidth + gap);
            rect.set(left, bottom - maxHeight * 0.25f * index, left + barWidth, bottom);
            paint.setColor(index <= bars ? activeColor : idleColor);
            canvas.drawRoundRect(rect, corner, corner, paint);
        }
    }
}
