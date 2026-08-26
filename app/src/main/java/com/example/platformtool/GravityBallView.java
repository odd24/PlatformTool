package com.example.platformtool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

public class GravityBallView extends View implements Choreographer.FrameCallback {
    private static final float ACCELERATION_SCALE = 180f;
    private static final float VELOCITY_DAMPING = 1.15f;
    private static final float EDGE_RESTITUTION = 0.35f;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF field = new RectF();
    private float ballX;
    private float ballY;
    private float velocityX;
    private float velocityY;
    private float accelerationX;
    private float accelerationY;
    private float ballRadius;
    private float density;
    private long lastFrameNanos;
    private boolean sensorEnabled;
    private boolean frameScheduled;

    public GravityBallView(Context context) {
        this(context, null);
    }

    public GravityBallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        ballRadius = 18f * density;
        backgroundPaint.setColor(Color.rgb(239, 248, 255));
        borderPaint.setColor(Color.rgb(23, 92, 211));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);
        guidePaint.setColor(Color.argb(70, 71, 84, 103));
        guidePaint.setStrokeWidth(1f * density);
        ballPaint.setColor(Color.rgb(2, 122, 72));
        highlightPaint.setColor(Color.argb(150, 255, 255, 255));
        textPaint.setColor(Color.rgb(71, 84, 103));
        textPaint.setTextSize(12f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        ballPaint.setShadowLayer(5f * density, 0f, 2f * density, Color.argb(100, 0, 0, 0));
    }

    public void setSensorEnabled(boolean enabled) {
        if (sensorEnabled == enabled) return;
        sensorEnabled = enabled;
        accelerationX = 0f;
        accelerationY = 0f;
        velocityX = 0f;
        velocityY = 0f;
        lastFrameNanos = 0L;
        ballX = getWidth() / 2f;
        ballY = getHeight() / 2f;
        if (enabled) scheduleFrame();
        else {
            if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this);
            frameScheduled = false;
            invalidate();
        }
    }

    public void setScreenAcceleration(float horizontal, float vertical) {
        if (!sensorEnabled) return;
        accelerationX = horizontal * ACCELERATION_SCALE;
        accelerationY = vertical * ACCELERATION_SCALE;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float inset = 8f * density;
        field.set(inset, inset, width - inset, height - inset);
        ballX = width / 2f;
        ballY = height / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float corner = 12f * density;
        canvas.drawRoundRect(field, corner, corner, backgroundPaint);
        canvas.drawRoundRect(field, corner, corner, borderPaint);
        canvas.drawLine(field.centerX(), field.top, field.centerX(), field.bottom, guidePaint);
        canvas.drawLine(field.left, field.centerY(), field.right, field.centerY(), guidePaint);
        canvas.drawText("上", field.centerX(), field.top + 18f * density, textPaint);
        canvas.drawText("下", field.centerX(), field.bottom - 8f * density, textPaint);
        canvas.drawText("左", field.left + 16f * density, field.centerY() + 4f * density, textPaint);
        canvas.drawText("右", field.right - 16f * density, field.centerY() + 4f * density, textPaint);

        if (sensorEnabled) {
            canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);
            canvas.drawCircle(ballX - ballRadius * 0.32f, ballY - ballRadius * 0.32f,
                    ballRadius * 0.25f, highlightPaint);
        } else {
            textPaint.setTextSize(14f * density);
            canvas.drawText("打开加速度计开关后开始方向测试",
                    field.centerX(), field.centerY() + 5f * density, textPaint);
            textPaint.setTextSize(12f * density);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        if (!sensorEnabled) return;
        if (lastFrameNanos != 0L) {
            float deltaSeconds = Math.min(0.033f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f);
            velocityX += accelerationX * deltaSeconds;
            velocityY += accelerationY * deltaSeconds;
            float damping = (float) Math.exp(-VELOCITY_DAMPING * deltaSeconds);
            velocityX *= damping;
            velocityY *= damping;
            ballX += velocityX * deltaSeconds;
            ballY += velocityY * deltaSeconds;
            constrainBall();
        }
        lastFrameNanos = frameTimeNanos;
        invalidate();
        scheduleFrame();
    }

    private void constrainBall() {
        float left = field.left + ballRadius;
        float right = field.right - ballRadius;
        float top = field.top + ballRadius;
        float bottom = field.bottom - ballRadius;
        if (ballX < left) {
            ballX = left;
            velocityX = Math.abs(velocityX) * EDGE_RESTITUTION;
        } else if (ballX > right) {
            ballX = right;
            velocityX = -Math.abs(velocityX) * EDGE_RESTITUTION;
        }
        if (ballY < top) {
            ballY = top;
            velocityY = Math.abs(velocityY) * EDGE_RESTITUTION;
        } else if (ballY > bottom) {
            ballY = bottom;
            velocityY = -Math.abs(velocityY) * EDGE_RESTITUTION;
        }
    }

    private void scheduleFrame() {
        if (frameScheduled || !sensorEnabled || !isAttachedToWindow()) return;
        frameScheduled = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sensorEnabled) scheduleFrame();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this);
        frameScheduled = false;
        super.onDetachedFromWindow();
    }
}
