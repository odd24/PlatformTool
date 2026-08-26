package com.example.platformtool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BatteryChartView extends View {
    private static final long HALF_HOUR_MS = 30L * 60L * 1000L;
    private static final int[] COLORS = {
            Color.rgb(23, 92, 211), Color.rgb(217, 45, 32),
            Color.rgb(2, 122, 72), Color.rgb(247, 144, 9)
    };
    private static final String[] NAMES = {"电量", "电流", "电压", "温度"};
    private static final String[] UNITS = {"%", "mA", "V", "℃"};

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF plot = new RectF();
    private final List<BatteryChartPoint> points = new ArrayList<>();

    public BatteryChartView(Context context) {
        this(context, null);
    }

    public BatteryChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textPaint.setColor(Color.rgb(71, 84, 103));
        textPaint.setTextSize(11f * density);
        gridPaint.setColor(Color.rgb(218, 224, 231));
        gridPaint.setStrokeWidth(1f * density);
        paint.setStrokeWidth(2.2f * density);
        paint.setStyle(Paint.Style.STROKE);
    }

    void setPoints(List<BatteryChartPoint> values) {
        points.clear();
        points.addAll(values);
        Collections.sort(points,
                (left, right) -> Long.compare(left.timestamp, right.timestamp));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(248, 250, 252));
        if (points.isEmpty()) return;

        float left = 58f * density;
        float top = 82f * density;
        float right = getWidth() - 18f * density;
        float bottom = getHeight() - 42f * density;
        if (right <= left || bottom <= top) return;
        plot.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(plot, 8f * density, 8f * density, paint);

        Range[] ranges = calculateRanges();
        drawLegend(canvas, ranges);
        drawHorizontalGrid(canvas);

        long minTime = points.get(0).timestamp;
        long maxTime = points.get(points.size() - 1).timestamp;
        if (maxTime <= minTime) {
            minTime -= HALF_HOUR_MS / 2;
            maxTime += HALF_HOUR_MS / 2;
        }
        drawTimeGrid(canvas, minTime, maxTime);
        for (int series = 0; series < 4; series++) {
            drawSeries(canvas, series, ranges[series], minTime, maxTime);
        }
    }

    private Range[] calculateRanges() {
        Range[] ranges = {new Range(), new Range(), new Range(), new Range()};
        for (BatteryChartPoint point : points) {
            ranges[0].include(point.level);
            ranges[1].include(point.current);
            ranges[2].include(point.voltage);
            ranges[3].include(point.temperature);
        }
        return ranges;
    }

    private void drawLegend(Canvas canvas, Range[] ranges) {
        float columnWidth = getWidth() / 2f;
        for (int index = 0; index < 4; index++) {
            float x = (index % 2) * columnWidth + 18f * density;
            float y = (22f + (index / 2) * 28f) * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLORS[index]);
            canvas.drawCircle(x, y - 4f * density, 4f * density, paint);
            textPaint.setColor(Color.rgb(16, 24, 40));
            textPaint.setTextAlign(Paint.Align.LEFT);
            String rangeText = ranges[index].valid
                    ? String.format(Locale.getDefault(), "%s  %.2f～%.2f %s",
                    NAMES[index], ranges[index].min, ranges[index].max, UNITS[index])
                    : NAMES[index] + "  无数据";
            canvas.drawText(rangeText, x + 10f * density, y, textPaint);
        }
    }

    private void drawHorizontalGrid(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setColor(Color.rgb(102, 112, 133));
        for (int index = 0; index <= 4; index++) {
            float ratio = index / 4f;
            float y = plot.bottom - ratio * plot.height();
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
            canvas.drawText((index * 25) + "%", plot.left - 7f * density,
                    y + 4f * density, textPaint);
        }
    }

    private void drawTimeGrid(Canvas canvas, long minTime, long maxTime) {
        long duration = maxTime - minTime;
        long halfHourCount = Math.max(1L,
                (duration + HALF_HOUR_MS - 1L) / HALF_HOUR_MS);
        long stride = Math.max(1L, (halfHourCount + 5L) / 6L) * HALF_HOUR_MS;
        long firstTick = ((minTime + stride - 1L) / stride) * stride;
        SimpleDateFormat format = new SimpleDateFormat(
                duration >= 24L * 60L * 60L * 1000L ? "MM-dd HH:mm" : "HH:mm",
                Locale.getDefault());
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.rgb(102, 112, 133));
        for (long time = firstTick; time <= maxTime; time += stride) {
            float x = timeToX(time, minTime, maxTime);
            canvas.drawLine(x, plot.top, x, plot.bottom, gridPaint);
            canvas.drawText(format.format(new Date(time)), x,
                    plot.bottom + 20f * density, textPaint);
            if (Long.MAX_VALUE - stride < time) break;
        }
        if (firstTick > minTime + stride / 3) {
            canvas.drawText(format.format(new Date(minTime)), plot.left,
                    plot.bottom + 20f * density, textPaint);
        }
    }

    private void drawSeries(Canvas canvas, int series, Range range,
                            long minTime, long maxTime) {
        if (!range.valid) return;
        Path path = new Path();
        boolean started = false;
        int validPoints = 0;
        float onlyX = 0f;
        float onlyY = 0f;
        for (BatteryChartPoint point : points) {
            float value = seriesValue(point, series);
            if (Float.isNaN(value)) {
                started = false;
                continue;
            }
            float x = timeToX(point.timestamp, minTime, maxTime);
            float normalized = range.max == range.min
                    ? 0.5f : (value - range.min) / (range.max - range.min);
            float y = plot.bottom - normalized * plot.height();
            if (!started) path.moveTo(x, y); else path.lineTo(x, y);
            started = true;
            validPoints++;
            onlyX = x;
            onlyY = y;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * density);
        paint.setColor(COLORS[series]);
        canvas.drawPath(path, paint);
        if (validPoints == 1) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(onlyX, onlyY, 4f * density, paint);
        }
    }

    private float timeToX(long time, long minTime, long maxTime) {
        return plot.left + (time - minTime) / (float) (maxTime - minTime) * plot.width();
    }

    private static float seriesValue(BatteryChartPoint point, int series) {
        switch (series) {
            case 0: return point.level;
            case 1: return point.current;
            case 2: return point.voltage;
            default: return point.temperature;
        }
    }

    private static final class Range {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        boolean valid;

        void include(float value) {
            if (Float.isNaN(value)) return;
            min = Math.min(min, value);
            max = Math.max(max, value);
            valid = true;
        }
    }
}
