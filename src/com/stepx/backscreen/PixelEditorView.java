package com.stepx.backscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 像素画布：直接点画点阵，拖动连续绘制。 */
public class PixelEditorView extends View {

    private Matrix matrix = new Matrix(32, 32);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean drawMode = true;
    private int onColor = 0xFFFF7A1A;
    private final RectF area = new RectF();

    public PixelEditorView(Context c) {
        super(c);
        grid.setColor(0xFF23262B);
        grid.setStrokeWidth(1f);
        grid.setStyle(Paint.Style.STROKE);
    }

    public void setMatrix(Matrix m) {
        if (m != null) matrix = m;
        invalidate();
    }

    public Matrix getDotMatrix() {
        return matrix;
    }

    public void setDrawMode(boolean draw) {
        drawMode = draw;
    }

    public void setOnColor(int color) {
        onColor = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = getResources().getDisplayMetrics().density * 6;
        area.set(pad, pad, getWidth() - pad, getHeight() - pad);
        p.setColor(0xFF07080A);
        canvas.drawRoundRect(area, pad, pad, p);

        float cw = area.width() / matrix.cols;
        float ch = area.height() / matrix.rows;
        for (int y = 0; y < matrix.rows; y++) {
            for (int x = 0; x < matrix.cols; x++) {
                float l = area.left + x * cw;
                float t = area.top + y * ch;
                p.setStyle(Paint.Style.FILL);
                if (matrix.get(x, y)) {
                    p.setColor(onColor);
                    canvas.drawRoundRect(new RectF(l + cw * 0.12f, t + ch * 0.12f,
                            l + cw * 0.88f, t + ch * 0.88f), cw * 0.2f, ch * 0.2f, p);
                } else {
                    p.setColor(0xFF14161A);
                    canvas.drawRect(l + 1, t + 1, l + cw - 1, t + ch - 1, p);
                }
            }
        }
        // 每 8 格画一条辅助线，方便对齐
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFF2A2E35);
        for (int x = 0; x <= matrix.cols; x += 8) {
            float l = area.left + x * cw;
            canvas.drawLine(l, area.top, l, area.bottom, p);
        }
        for (int y = 0; y <= matrix.rows; y += 8) {
            float t = area.top + y * ch;
            canvas.drawLine(area.left, t, area.right, t, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float cw = area.width() / matrix.cols;
                float ch = area.height() / matrix.rows;
                int x = (int) ((e.getX() - area.left) / cw);
                int y = (int) ((e.getY() - area.top) / ch);
                if (x < 0 || y < 0 || x >= matrix.cols || y >= matrix.rows) return true;
                boolean on = drawMode;
                if (matrix.get(x, y) != on) {
                    matrix.set(x, y, on);
                    invalidate();
                    if (listener != null) listener.onPixelChanged(matrix);
                }
                return true;
            }
            default:
                return true;
        }
    }

    public interface Listener {
        void onPixelChanged(Matrix m);
    }

    private Listener listener;

    public void setListener(Listener l) {
        listener = l;
    }
}
