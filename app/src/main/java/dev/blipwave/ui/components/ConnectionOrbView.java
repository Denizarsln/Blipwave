package dev.blipwave.ui.components;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;

import dev.blipwave.R;
import dev.blipwave.ble.model.ConnectionState;

/**
 * Bağlantı durumunun görsel özeti:
 * Bağlanıyor: dönen gradyan yay + yayılan halkalar;
 * Bağlandı: yaylanarak "oturan" parlak halka;
 * Koptu: mercan rengi kesikli halka.
 */
public class ConnectionOrbView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final Matrix matrix = new Matrix();
    private final ValueAnimator spin = ValueAnimator.ofFloat(0f, 1f);
    private final DashPathEffect dash;
    private final float strokeWidth;
    private final int trackColor;
    private final int violet;
    private final int pink;
    private final int cyan;

    private ConnectionState state = ConnectionState.IDLE;
    private int accent;
    private ValueAnimator colorAnimator;
    private Drawable icon;

    public ConnectionOrbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = Ui.dp(context, 6);
        dash = new DashPathEffect(new float[]{Ui.dp(context, 14), Ui.dp(context, 12)}, 0);
        trackColor = ColorUtils.setAlphaComponent(Ui.color(context, R.color.bp_on_background), 31);
        violet = Ui.color(context, R.color.electric_violet);
        pink = Ui.color(context, R.color.hot_pink);
        cyan = Ui.color(context, R.color.neon_cyan);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        spin.setDuration(1_100L);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(animation -> invalidate());

        accent = Ui.statusColor(context, state);
        icon = iconFor(state);
    }

    public void setState(ConnectionState newState) {
        if (newState.equals(state)) return;
        ConnectionState previous = state;
        state = newState;
        icon = iconFor(newState);

        if (colorAnimator != null) colorAnimator.cancel();
        colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), accent, Ui.statusColor(getContext(), newState));
        colorAnimator.setDuration(500L);
        colorAnimator.addUpdateListener(animation -> {
            accent = (int) animation.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();

        // Bağlantı kurulduğu an küre küçülüp yaylanarak geri gelir
        if (newState.isConnected() && !previous.isConnected()) {
            setScaleX(0.8f);
            setScaleY(0.8f);
            animate().scaleX(1f).scaleY(1f).setDuration(500L).setInterpolator(new OvershootInterpolator(3f)).start();
        }
        updateSpin();
    }

    private Drawable iconFor(ConnectionState value) {
        int res;
        switch (value.status) {
            case CONNECTED: res = R.drawable.ic_bluetooth_connected; break;
            case CONNECTING: res = R.drawable.ic_bluetooth_searching; break;
            case DISCONNECTED: res = value.unexpected ? R.drawable.ic_bluetooth_disabled : R.drawable.ic_bluetooth; break;
            case IDLE:
            default: res = R.drawable.ic_bluetooth; break;
        }
        Drawable drawable = AppCompatResources.getDrawable(getContext(), res);
        if (drawable == null) throw new IllegalStateException("Eksik ikon");
        return drawable.mutate();
    }

    private void updateSpin() {
        if (state.status == ConnectionState.Status.CONNECTING && isAttachedToWindow()) {
            if (!spin.isStarted()) spin.start();
        } else {
            spin.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        spin.cancel();
        if (colorAnimator != null) colorAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        // Bağlıyken çizilen dış ışıltı radius + 2 × kalınlığa kadar uzanır; görünüm sınırında kesilmesin
        float radius = Math.min(getWidth(), getHeight()) / 2f - strokeWidth * 2.5f;
        ringPaint.setPathEffect(null);
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(strokeWidth);

        switch (state.status) {
            case CONNECTING: {
                float progress = spin.isRunning() ? (float) spin.getAnimatedValue() : 0f;
                // Dışa yayılan iki halka
                ringPaint.setStrokeWidth(Ui.dp(getContext(), 2));
                for (int ring = 0; ring < 2; ring++) {
                    float p = (progress + ring * 0.5f) % 1f;
                    ringPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (90 * (1f - p))));
                    canvas.drawCircle(cx, cy, radius * (0.7f + 0.3f * p) + strokeWidth, ringPaint);
                }
                ringPaint.setStrokeWidth(strokeWidth);
                ringPaint.setColor(trackColor);
                canvas.drawCircle(cx, cy, radius, ringPaint);
                // Dönen gradyan yay
                SweepGradient gradient = new SweepGradient(cx, cy,
                        new int[]{Color.TRANSPARENT, accent, pink}, new float[]{0f, 0.6f, 0.83f});
                matrix.setRotate(progress * 360f, cx, cy);
                gradient.setLocalMatrix(matrix);
                ringPaint.setShader(gradient);
                arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.save();
                canvas.rotate(progress * 360f, cx, cy);
                canvas.drawArc(arcRect, 0f, 300f, false, ringPaint);
                canvas.restore();
                break;
            }
            case CONNECTED: {
                ringPaint.setColor(ColorUtils.setAlphaComponent(accent, 46));
                ringPaint.setStrokeWidth(strokeWidth * 2);
                canvas.drawCircle(cx, cy, radius + strokeWidth, ringPaint);
                ringPaint.setStrokeWidth(strokeWidth);
                ringPaint.setShader(new SweepGradient(cx, cy, new int[]{cyan, violet, pink, cyan}, null));
                canvas.drawCircle(cx, cy, radius, ringPaint);
                break;
            }
            case DISCONNECTED:
                if (state.unexpected) {
                    ringPaint.setColor(accent);
                    ringPaint.setPathEffect(dash);
                    canvas.drawCircle(cx, cy, radius, ringPaint);
                    break;
                }
                // Kullanıcı kesti: sade halka
            case IDLE:
            default:
                ringPaint.setColor(trackColor);
                canvas.drawCircle(cx, cy, radius, ringPaint);
                break;
        }

        // İç disk ve ikon
        float inner = radius - Ui.dp(getContext(), 16);
        fillPaint.setColor(ColorUtils.setAlphaComponent(accent, 41));
        canvas.drawCircle(cx, cy, inner, fillPaint);
        icon.setTint(accent);
        int half = (int) Ui.dp(getContext(), 20);
        icon.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
        icon.draw(canvas);
    }
}
