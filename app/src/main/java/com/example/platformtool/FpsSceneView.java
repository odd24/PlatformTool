package com.example.platformtool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class FpsSceneView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private boolean dynamic;
    private float phase;

    public FpsSceneView(Context context) {
        this(context, null);
    }

    public FpsSceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setDynamic(boolean dynamic) {
        if (this.dynamic == dynamic) return;
        this.dynamic = dynamic;
        phase = 0f;
        invalidate();
    }

    public void setAnimationPhase(float phase) {
        if (!dynamic) return;
        this.phase = phase;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(Color.rgb(15, 23, 42));
        drawCheckerboard(canvas, width, height);
        drawColorBars(canvas, width, height);
        if (dynamic) drawDynamicObjects(canvas, width, height);
        drawLabel(canvas, width, height);
    }

    private void drawCheckerboard(Canvas canvas, int width, int height) {
        float size = Math.max(28f * density, Math.min(width, height) / 12f);
        int rows = (int) Math.ceil(height / size);
        int columns = (int) Math.ceil(width / size);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                boolean light = ((row + column) & 1) == 0;
                paint.setColor(light ? Color.rgb(30, 41, 59) : Color.rgb(15, 23, 42));
                canvas.drawRect(column * size, row * size,
                        Math.min(width, (column + 1) * size),
                        Math.min(height, (row + 1) * size), paint);
            }
        }
    }

    private void drawColorBars(Canvas canvas, int width, int height) {
        int[] colors = {
                Color.rgb(23, 92, 211), Color.rgb(2, 122, 72), Color.rgb(247, 144, 9),
                Color.rgb(217, 45, 32), Color.rgb(105, 65, 198)
        };
        float top = height * 0.12f;
        float barHeight = Math.max(18f * density, height * 0.055f);
        float segmentWidth = width / (float) colors.length;
        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);
            canvas.drawRect(i * segmentWidth, top, (i + 1) * segmentWidth,
                    top + barHeight, paint);
        }
    }

    private void drawDynamicObjects(Canvas canvas, int width, int height) {
        float usableWidth = Math.max(1f, width - 80f * density);
        float x = 40f * density + (float) ((Math.sin(phase) + 1f) * 0.5f) * usableWidth;
        float y = height * 0.55f + (float) Math.sin(phase * 1.7f) * height * 0.22f;
        float radius = Math.max(18f * density, Math.min(width, height) * 0.055f);
        paint.setColor(Color.rgb(18, 183, 106));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(Color.argb(180, 255, 255, 255));
        canvas.drawCircle(x - radius * 0.3f, y - radius * 0.3f, radius * 0.22f, paint);

        float centerX = width * 0.5f;
        float centerY = height * 0.58f;
        strokePaint.setColor(Color.rgb(132, 202, 255));
        canvas.save();
        canvas.rotate((float) Math.toDegrees(phase * 0.8f), centerX, centerY);
        for (int i = 0; i < 12; i++) {
            canvas.rotate(30f, centerX, centerY);
            canvas.drawLine(centerX, centerY - radius * 1.4f,
                    centerX, centerY - radius * 2.5f, strokePaint);
        }
        canvas.restore();

        float stripeWidth = Math.max(12f * density, width / 30f);
        float offset = (phase * 100f * density) % (stripeWidth * 2f);
        paint.setColor(Color.argb(150, 255, 255, 255));
        for (float stripeX = -stripeWidth * 2f + offset; stripeX < width; stripeX += stripeWidth * 2f) {
            rect.set(stripeX, height * 0.88f, stripeX + stripeWidth, height * 0.94f);
            canvas.drawRect(rect, paint);
        }
    }

    private void drawLabel(Canvas canvas, int width, int height) {
        paint.setColor(Color.argb(180, 0, 0, 0));
        rect.set(width * 0.25f, height * 0.42f, width * 0.75f, height * 0.52f);
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint);
        canvas.drawText(dynamic ? "动态测试画面" : "静态测试画面",
                width * 0.5f, height * 0.485f, textPaint);
    }
}
