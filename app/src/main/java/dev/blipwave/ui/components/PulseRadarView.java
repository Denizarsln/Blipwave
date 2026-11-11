package dev.blipwave.ui.components;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;

import dev.blipwave.R;

/**
 * Tarama radarı: merkezden yayılan pulse halkaları, dönen tarama ışını ve
 * bulunan cihaz sayısı kadar yanıp sönen "blip" noktaları.
 */
public class PulseRadarView extends View {

    private static final int MAX_BLIPS = 10;
    private static final long PULSE_MILLIS = 2_600L;
    private static final long SWEEP_MILLIS = 3_200L;

    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final Matrix sweepMatrix = new Matrix();

    private final int violet;
    private final int coral;
    private final int cyan;
    private final int pink;

    private final Drawable idleIcon;
    private final Drawable activeIcon;

    private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
    private ValueAnimator activityAnimator;

    private boolean active;
    /** 0 = duruyor, 1 = tam tarama; başlat/durdur geçişinde yumuşakça değişir. */
    private float activity;
    private int blipCount;
    private long startMillis;

    public PulseRadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        violet = Ui.color(context, R.color.electric_violet);
        coral = Ui.color(context, R.color.coral);
        cyan = Ui.color(context, R.color.neon_cyan);
        pink = Ui.color(context, R.color.hot_pink);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(Ui.dp(context, 1));
        guidePaint.setColor(ColorUtils.setAlphaComponent(Ui.color(context, R.color.bp_on_background), 31));
        ringPaint.setStyle(Paint.Style.STROKE);

        idleIcon = tinted(R.drawable.ic_bluetooth);
        activeIcon = tinted(R.drawable.ic_bluetooth_searching);

        // Tek bir sonsuz saat; pulse ve tarama açıları geçen süreden hesaplanır
        clock.setDuration(1_000L);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(animation -> invalidate());
    }

    private Drawable tinted(int res) {
        Drawable drawable = AppCompatResources.getDrawable(getContext(), res);
        if (drawable == null) throw new IllegalStateException("Eksik ikon");
        drawable = drawable.mutate();
        drawable.setTint(Color.WHITE);
        return drawable;
    }

    public void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (activityAnimator != null) activityAnimator.cancel();
        activityAnimator = ValueAnimator.ofFloat(activity, value ? 1f : 0f);
        activityAnimator.setDuration(700L);
        activityAnimator.addUpdateListener(animation -> {
            activity = (float) animation.getAnimatedValue();
            invalidate();
        });
        activityAnimator.start();
        if (value && !clock.isStarted() && isAttachedToWindow()) clock.start();
    }

    public void setBlipCount(int count) {
        if (blipCount == count) return;
        blipCount = count;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startMillis = System.currentTimeMillis();
        clock.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        clock.cancel();
        if (activityAnimator != null) activityAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(getWidth(), getHeight()) / 2f;
        long elapsed = System.currentTimeMillis() - startMillis;
        float pulse = (elapsed % PULSE_MILLIS) / (float) PULSE_MILLIS;
        float sweep = 360f * (elapsed % SWEEP_MILLIS) / SWEEP_MILLIS;

        // Sabit kılavuz halkaları
        for (int ring = 1; ring <= 3; ring++) canvas.drawCircle(cx, cy, maxRadius * ring / 3f, guidePaint);

        if (activity > 0.01f) {
            // Dışa doğru yayılıp sönen pulse halkaları (mordan mercana)
            for (int ring = 0; ring < 3; ring++) {
                float progress = (pulse + ring / 3f) % 1f;
                int color = (int) argb.evaluate(progress, violet, coral);
                ringPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * (1f - progress) * 0.7f * activity)));
                ringPaint.setStrokeWidth(Ui.dp(getContext(), 1) + Ui.dp(getContext(), 4) * (1f - progress));
                canvas.drawCircle(cx, cy, maxRadius * progress, ringPaint);
            }

            // Dönen tarama ışını: arkasında sönen camgöbeği kuyruk
            int head = ColorUtils.setAlphaComponent(cyan, (int) (97 * activity));
            SweepGradient gradient = new SweepGradient(cx, cy,
                    new int[]{Color.TRANSPARENT, Color.TRANSPARENT, head, Color.TRANSPARENT},
                    new float[]{0f, 0.75f, 0.999f, 1f});
            sweepMatrix.setRotate(sweep, cx, cy);
            gradient.setLocalMatrix(sweepMatrix);
            sweepPaint.setShader(gradient);
            canvas.drawCircle(cx, cy, maxRadius, sweepPaint);

            // Bulunan cihazları temsil eden noktalar (altın açı ile dağıtılır)
            int count = Math.min(blipCount, MAX_BLIPS);
            for (int index = 0; index < count; index++) {
                double angle = Math.toRadians(index * 137.5);
                float distance = maxRadius * (0.42f + (index % 4) * 0.14f);
                float twinkle = 0.45f + 0.55f * ((pulse + index * 0.17f) % 1f);
                float x = cx + (float) (Math.cos(angle) * distance);
                float y = cy + (float) (Math.sin(angle) * distance);
                blipPaint.setColor(ColorUtils.setAlphaComponent(cyan, (int) (64 * twinkle * activity)));
                canvas.drawCircle(x, y, Ui.dp(getContext(), 7), blipPaint);
                blipPaint.setColor(ColorUtils.setAlphaComponent(cyan, (int) (255 * twinkle * activity)));
                canvas.drawCircle(x, y, Ui.dp(getContext(), 3), blipPaint);
            }
        }

        // Merkez küre: tararken hafifçe nefes alır
        float breathe = (float) Math.sin(elapsed / 900.0 * Math.PI) * 0.06f * activity;
        float orbRadius = Ui.dp(getContext(), 38) * (1f + breathe);
        orbPaint.setShader(new LinearGradient(cx - orbRadius, cy - orbRadius, cx + orbRadius, cy + orbRadius,
                new int[]{violet, pink, coral}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, orbRadius, orbPaint);

        Drawable icon = active ? activeIcon : idleIcon;
        int half = (int) Ui.dp(getContext(), 17);
        icon.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
        icon.draw(canvas);

        // Duruyorken ve geçiş bittiyse saati durdur, pil harcamasın
        if (!active && activity <= 0.01f && clock.isRunning()) clock.cancel();
    }
}
