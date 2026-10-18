package com.stepx.backscreen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * 点阵数据模型：cols x rows 的开关矩阵，并提供文本/图片/二维码到点阵的转换与渲染。
 */
public final class Matrix {
    public final int cols;
    public final int rows;
    public final boolean[] cells;

    public Matrix(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.cells = new boolean[this.cols * this.rows];
    }

    public Matrix copy() {
        Matrix m = new Matrix(cols, rows);
        System.arraycopy(cells, 0, m.cells, 0, cells.length);
        return m;
    }

    public boolean get(int x, int y) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) return false;
        return cells[y * cols + x];
    }

    public void set(int x, int y, boolean on) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) return;
        cells[y * cols + x] = on;
    }

    public void toggle(int x, int y) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) return;
        cells[y * cols + x] = !cells[y * cols + x];
    }

    public void clear() {
        for (int i = 0; i < cells.length; i++) cells[i] = false;
    }

    public void fill() {
        for (int i = 0; i < cells.length; i++) cells[i] = true;
    }

    public int onCount() {
        int n = 0;
        for (boolean b : cells) if (b) n++;
        return n;
    }

    /** 自适应缩放：把当前矩阵按最近邻搬到新尺寸（用于切换点阵规格时保留内容）。 */
    public Matrix resized(int newCols, int newRows) {
        Matrix m = new Matrix(newCols, newRows);
        for (int y = 0; y < newRows; y++) {
            int sy = y * rows / newRows;
            for (int x = 0; x < newCols; x++) {
                int sx = x * cols / newCols;
                m.cells[y * newCols + x] = get(sx, sy);
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- 渲染

    /** 预览渲染：圆点 + 轻微光晕，底色透明由外层负责。 */
    public void draw(Canvas canvas, RectF area, int onColor, float gapRatio, boolean round) {
        float cellW = area.width() / cols;
        float cellH = area.height() / rows;
        float size = Math.min(cellW, cellH);
        float gap = size * gapRatio;
        float left = area.left + (area.width() - cellW * cols) / 2f;
        float top = area.top + (area.height() - cellH * rows) / 2f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float cx = left + x * cellW + cellW / 2f;
                float cy = top + y * cellH + cellH / 2f;
                if (get(x, y)) {
                    p.setColor(onColor);
                    p.setAlpha(40);
                    float halo = size * 0.85f;
                    canvas.drawCircle(cx, cy, halo, p);
                    p.setColor(onColor);
                    p.setAlpha(255);
                } else {
                    p.setColor(0xFF1A1C20);
                    p.setAlpha(255);
                }
                float r = Math.max(1f, (size - gap) / 2f);
                if (round) {
                    canvas.drawCircle(cx, cy, r, p);
                } else {
                    canvas.drawRect(cx - r, cy - r, cx + r, cy + r, p);
                }
            }
        }
    }

    // ------------------------------------------------------- 文本 -> 点阵

    /**
     * 把任意文本渲染为点阵：先用系统字体绘制到高分辨率位图，再按单元亮度二值化采样，
     * 因此中文、符号、emoji 之外的字符都能正确落点。
     */
    public static Matrix fromText(Context ctx, String text, int cols, int rows, boolean bold) {
        Matrix m = new Matrix(cols, rows);
        if (text == null) return m;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return m;

        String[] lines = normalized.split("\n", -1);
        int lineCount = lines.length;

        // 采样倍率：每个点阵单元用 12x12 像素绘制，保证细小笔画也能被采到
        final int S = 12;
        Bitmap bmp = Bitmap.createBitmap(cols * S, rows * S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.BLACK);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setSubpixelText(true);

        float boxW = cols * S;
        float boxH = rows * S;
        float lineHeight = boxH / lineCount;

        for (int i = 0; i < lineCount; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            float maxTextWidth = boxW * 0.94f;
            float size = Math.min(lineHeight * 0.78f, maxTextWidth / Math.max(1, visualWidth(line)) * 1.1f);
            size = Math.max(6f, size);
            p.setTextSize(size);
            float w = p.measureText(line);
            if (w > maxTextWidth) {
                p.setTextSize(size * maxTextWidth / w);
            }
            Paint.FontMetrics fm = p.getFontMetrics();
            float baseline = i * lineHeight + lineHeight / 2f - (fm.ascent + fm.descent) / 2f;
            c.drawText(line, boxW / 2f, baseline, p);
        }

        int[] pixels = new int[cols * S * rows * S];
        bmp.getPixels(pixels, 0, cols * S, 0, 0, cols * S, rows * S);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                long sum = 0;
                for (int dy = 0; dy < S; dy++) {
                    int base = (y * S + dy) * cols * S + x * S;
                    for (int dx = 0; dx < S; dx++) {
                        int px = pixels[base + dx];
                        sum += (px & 0xFF) + ((px >> 8) & 0xFF) + ((px >> 16) & 0xFF);
                    }
                }
                int avg = (int) (sum / (S * S * 3));
                m.cells[y * cols + x] = avg > 60;
            }
        }
        bmp.recycle();
        return m;
    }

    /** 全角字符按 2 个宽度计算，让中英混排更接近等宽；用于估算字号。 */
    private static int visualWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += (ch > 0x2E80) ? 2 : 1;
        }
        return Math.max(1, w);
    }

    // ------------------------------------------------------- 图片 -> 点阵

    /**
     * 图片转点阵，会自动按比例居中裁剪并采样。
     *
     * @param threshold 0-255，越大越亮才点亮
     * @param dither    true 时用有序抖动保留灰阶层次
     */
    public static Matrix fromBitmap(Bitmap src, int cols, int rows, int threshold, boolean dither) {
        Matrix m = new Matrix(cols, rows);
        if (src == null) return m;

        // 居中裁剪为目标宽高比
        int sw = src.getWidth();
        int sh = src.getHeight();
        double targetRatio = (double) cols / rows;
        double srcRatio = (double) sw / sh;
        int cw;
        int ch;
        if (srcRatio > targetRatio) {
            ch = sh;
            cw = (int) Math.round(sh * targetRatio);
        } else {
            cw = sw;
            ch = (int) Math.round(sw / targetRatio);
        }
        int cx = Math.max(0, (sw - cw) / 2);
        int cy = Math.max(0, (sh - ch) / 2);
        Bitmap crop = Bitmap.createBitmap(src, cx, cy, cw, ch);

        int[] pixels = new int[cols * rows];
        // 逐单元采样平均亮度
        for (int y = 0; y < rows; y++) {
            int y0 = y * ch / rows;
            int y1 = Math.max(y0 + 1, (y + 1) * ch / rows);
            for (int x = 0; x < cols; x++) {
                int x0 = x * cw / cols;
                int x1 = Math.max(x0 + 1, (x + 1) * cw / cols);
                long sum = 0;
                int count = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int px = crop.getPixel(Math.min(sx, cw - 1), Math.min(sy, ch - 1));
                        int r = (px >> 16) & 0xFF;
                        int g = (px >> 8) & 0xFF;
                        int b = px & 0xFF;
                        sum += (r * 299 + g * 587 + b * 114) / 1000;
                        count++;
                    }
                }
                pixels[y * cols + x] = count == 0 ? 0 : (int) (sum / count);
            }
        }
        crop.recycle();

        // 4x4 Bayer 抖动矩阵
        int[][] bayer = {
                {0, 8, 2, 10},
                {12, 4, 14, 6},
                {3, 11, 1, 9},
                {15, 7, 13, 5}
        };
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int v = pixels[y * cols + x];
                if (dither) {
                    int t = (int) Math.round((bayer[y % 4][x % 4] + 0.5) / 16.0 * 255.0);
                    v = v + (128 - t) / 2;
                }
                m.cells[y * cols + x] = v >= threshold;
            }
        }
        return m;
    }


    /** 把 src 叠加到当前矩阵的 (ox, oy) 位置。 */
    public void blit(Matrix src, int ox, int oy) {
        if (src == null) return;
        for (int y = 0; y < src.rows; y++) {
            for (int x = 0; x < src.cols; x++) {
                if (src.get(x, y)) set(ox + x, oy + y, true);
            }
        }
    }

    // ------------------------------------------------- 内部图标（13x13）

    /** 简单图形：用于状态画面。kind: 0=IDLE 1=BUSY 2=WAIT */
    public void stampStateGlyph(int kind, int left, int top) {
        String[] idle = {
                "....###....",
                "...#####...",
                "..###.###..",
                ".#########.",
                "###.###.###",
                "###########",
                ".#########.",
                "..###.###..",
                "...#####...",
                "....###....",
                "..........."
        };
        String[] busy = {
                "....###....",
                "....###....",
                "....###....",
                "....###....",
                "..#######..",
                "###########",
                "..#######..",
                "....###....",
                "....###....",
                "....###....",
                "....###...."
        };
        String[] wait = {
                "....###....",
                "...#####...",
                "..###.###..",
                ".###...###.",
                "####...####",
                "###.....###",
                "###.....###",
                ".###...###.",
                "..###.###..",
                "...#####...",
                "....###...."
        };
        String[] g = kind == 1 ? busy : (kind == 2 ? wait : idle);
        for (int y = 0; y < g.length; y++) {
            for (int x = 0; x < g[y].length(); x++) {
                if (g[y].charAt(x) == '#') set(left + x, top + y, true);
            }
        }
    }
}
