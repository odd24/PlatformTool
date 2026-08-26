package com.example.platformtool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BatteryRecordTableView extends View {
    private static final String[] HEADERS = {
            "时间戳", "触发原因", "电量", "电流 (mA)",
            "电压 (V)", "温度 (℃)", "充电", "供电来源"
    };
    private static final float[] COLUMN_DP = {190, 105, 70, 105, 95, 95, 70, 125};

    private final float density;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String[]> rows = new ArrayList<>();
    private final float rowHeight;
    private final float tableWidth;
    private boolean showHeader = true;

    public BatteryRecordTableView(Context context) {
        this(context, null);
    }

    public BatteryRecordTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rowHeight = 38f * density;
        float width = 0f;
        for (float value : COLUMN_DP) width += value * density;
        tableWidth = width;
        textPaint.setTextSize(13f * density);
        textPaint.setColor(Color.rgb(16, 24, 40));
        linePaint.setColor(Color.rgb(208, 213, 221));
        linePaint.setStrokeWidth(density);
        setBackgroundColor(Color.WHITE);
    }

    void setRows(List<String[]> records) {
        rows.clear();
        rows.addAll(records);
        requestLayout();
        invalidate();
    }

    void setShowHeader(boolean show) {
        if (showHeader == show) return;
        showHeader = show;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.round(tableWidth);
        int visibleRows = rows.size() + (showHeader ? 1 : 0);
        int height = Math.round(rowHeight * Math.max(1, visibleRows));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int rowOffset = 0;
        if (showHeader) {
            drawRow(canvas, HEADERS, 0, true);
            rowOffset = 1;
        }
        for (int index = 0; index < rows.size(); index++) {
            drawRow(canvas, rows.get(index), index + rowOffset, false);
        }
    }

    private void drawRow(Canvas canvas, String[] values, int row, boolean header) {
        float top = row * rowHeight;
        fillPaint.setColor(header ? Color.rgb(234, 236, 240)
                : (row % 2 == 0 ? Color.rgb(249, 250, 251) : Color.WHITE));
        canvas.drawRect(0, top, tableWidth, top + rowHeight, fillPaint);
        float x = 0f;
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = top + (rowHeight - metrics.bottom - metrics.top) / 2f;
        textPaint.setFakeBoldText(header);
        for (int column = 0; column < COLUMN_DP.length; column++) {
            float width = COLUMN_DP[column] * density;
            canvas.drawLine(x, top, x, top + rowHeight, linePaint);
            String value = column < values.length && values[column] != null
                    ? values[column] : "";
            canvas.save();
            canvas.clipRect(x + 6f * density, top, x + width - 5f * density,
                    top + rowHeight);
            canvas.drawText(value, x + 7f * density, baseline, textPaint);
            canvas.restore();
            x += width;
        }
        canvas.drawLine(x, top, x, top + rowHeight, linePaint);
        canvas.drawLine(0, top + rowHeight, tableWidth, top + rowHeight, linePaint);
        textPaint.setFakeBoldText(false);
    }
}
