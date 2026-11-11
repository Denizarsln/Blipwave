package dev.blipwave.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.Collections;
import java.util.List;

import dev.blipwave.R;
import dev.blipwave.data.RssiHistory;

/**
 * Son 60 saniyenin RSSI çizgi grafiği. Canlıyken zaman ekseni her karede kayar,
 * böylece saniyede bir gelen örnekler arasında grafik akıcı görünür.
 */
public class RssiChartView extends View {

    private static final float MIN_RSSI = -100f;
    private static final float MAX_RSSI = -30f;
    private static final int[] GRID_LEVELS = {-40, -60, -80};

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final Path area = new Path();
    private final float labelGutter;
    private final int violet;
    private final int pink;
    private final int cyan;

    private List<RssiHistory.Sample> samples = Collections.emptyList();
    private boolean live;

    public RssiChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        violet = Ui.color(context, R.color.electric_violet);
        pink = Ui.color(context, R.color.hot_pink);
        cyan = Ui.color(context, R.color.neon_cyan);
        labelGutter = Ui.dp(context, 34);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Ui.dp(context, 1));
        gridPaint.setColor(Ui.color(context, R.color.bp_grid));
        gridPaint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 6), Ui.dp(context, 6)}, 0));

        labelPaint.setColor(Ui.color(context, R.color.bp_on_surface_variant));
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        labelPaint.setTextAlign(Paint.Align.RIGHT);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Ui.dp(context, 3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setSamples(List<RssiHistory.Sample> value, boolean isLive) {
        samples = value;
        live = isLive;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float chartWidth = w - labelGutter;
        linePaint.setShader(new LinearGradient(0, 0, chartWidth, 0, new int[]{violet, pink, cyan}, null, Shader.TileMode.CLAMP));
        areaPaint.setShader(new LinearGradient(0, 0, 0, h, ColorUtils.setAlphaComponent(violet, 97), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
    }

    private float yFor(float rssi, float height) {
        float clamped = Math.max(MIN_RSSI, Math.min(MAX_RSSI, rssi));
        return height * (1f - (clamped - MIN_RSSI) / (MAX_RSSI - MIN_RSSI));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float chartWidth = getWidth() - labelGutter;
        float height = getHeight();

        for (int level : GRID_LEVELS) {
            float y = yFor(level, height);
            canvas.drawLine(0, y, chartWidth, y, gridPaint);
            canvas.drawText(String.valueOf(level), getWidth(), y + labelPaint.getTextSize() / 3f, labelPaint);
        }
        if (samples.isEmpty()) return;

        long now = live ? System.currentTimeMillis() : samples.get(samples.size() - 1).timestampMillis;
        float[] xs = new float[samples.size()];
        float[] ys = new float[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            RssiHistory.Sample sample = samples.get(i);
            xs[i] = chartWidth * (1f - (now - sample.timestampMillis) / (float) RssiHistory.WINDOW_MILLIS);
            ys[i] = yFor(sample.rssi, height);
        }

        line.reset();
        line.moveTo(xs[0], ys[0]);
        // Komşu noktaların orta x'inde kontrol noktası: yumuşak ama taşmayan eğri
        for (int i = 1; i < xs.length; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            line.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }
        area.reset();
        area.addPath(line);
        area.lineTo(xs[xs.length - 1], height);
        area.lineTo(xs[0], height);
        area.close();

        canvas.save();
        canvas.clipRect(0, 0, chartWidth, height);
        canvas.drawPath(area, areaPaint);
        canvas.drawPath(line, linePaint);
        float headX = xs[xs.length - 1];
        float headY = ys[ys.length - 1];
        headPaint.setColor(ColorUtils.setAlphaComponent(cyan, 77));
        canvas.drawCircle(headX, headY, Ui.dp(getContext(), 9), headPaint);
        headPaint.setColor(cyan);
        canvas.drawCircle(headX, headY, Ui.dp(getContext(), 4.5f), headPaint);
        canvas.restore();

        if (live) postInvalidateOnAnimation();
    }
}
