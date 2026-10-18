package com.stepx.backscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 背屏点阵预览视图。 */
public class DotMatrixView extends View {

    private Matrix matrix;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area = new RectF();
    private int onColor = 0xFFFF7A1A;

    public DotMatrixView(Context context) {
        this(context, null);
    }

    public DotMatrixView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bg.setColor(0xFF07080A);
    }

    public void setMatrix(Matrix m) {
        this.matrix = m;
        invalidate();
    }

    public void setOnColor(int color) {
        this.onColor = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        if (size == 0) size = (int) (240 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 屏体底色
        canvas.drawColor(Color.TRANSPARENT);
        RectF outer = new RectF(0, 0, getWidth(), getHeight());
        bg.setColor(0xFF050608);
        canvas.drawRoundRect(outer, dp(18), dp(18), bg);

        if (matrix == null) return;
        float pad = dp(10);
        area.set(pad, pad, getWidth() - pad, getHeight() - pad);
        matrix.draw(canvas, area, onColor, 0.42f, true);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
