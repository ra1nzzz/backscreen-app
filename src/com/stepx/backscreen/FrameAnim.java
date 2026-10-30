package com.stepx.backscreen;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮播帧生成。
 *
 * 真机背屏是 17×23（391 格）单色点阵，一次只放得下一个汉字。
 * 所以这里把文本拆成两种帧序列：
 *   - SINGLE_CHAR：一个字一帧，每帧满屏居中，最清晰；
 *   - MARQUEE：把每个字做成一条横向长带，窗口按 step 列平移，形成跑马灯。
 *
 * 帧率由外部预览/下发控制；本类只负责生成 0/1 矩阵序列，不关心时间。
 */
public final class FrameAnim {

    public static final int NONE = 0;
    public static final int SINGLE_CHAR = 1;
    public static final int MARQUEE = 2;

    public static final String[] NAMES = {"关", "单字", "跑马灯"};

    private FrameAnim() {
    }

    /** 去掉空白后逐字成帧：一次只有一个字，占满点阵。 */
    public static List<Matrix> singleChar(Context ctx, String text, int cols, int rows, boolean bold) {
        List<Matrix> out = new ArrayList<Matrix>();
        for (String ch : chars(text)) {
            out.add(Matrix.fromText(ctx, ch, cols, rows, bold));
        }
        return out;
    }

    /**
     * 跑马灯：每个字单独渲染成 cols×rows，再横向拼接成一条长带，
     * 用 cols 宽的窗口按 step 列右移，直到整条带子完全移出画面。
     *
     * @param step 每帧平移的列数（1 = 逐列滚动，越小越顺滑、帧数越多）
     */
    public static List<Matrix> marquee(Context ctx, String text, int cols, int rows,
                                       boolean bold, int step) {
        List<String> chars = chars(text);
        if (chars.isEmpty()) return new ArrayList<Matrix>();
        int s = Math.max(1, step);

        List<Matrix> cells = new ArrayList<Matrix>();
        for (String ch : chars) cells.add(Matrix.fromText(ctx, ch, cols, rows, bold));

        int stripW = cells.size() * cols;
        List<Matrix> out = new ArrayList<Matrix>();
        for (int off = 0; off <= stripW; off += s) {
            Matrix frame = new Matrix(cols, rows);
            for (int x = 0; x < cols; x++) {
                int sx = off + x;
                if (sx >= stripW) break;
                Matrix cell = cells.get(sx / cols);
                int cx = sx % cols;
                for (int y = 0; y < rows; y++) {
                    if (cell.get(cx, y)) frame.set(x, y, true);
                }
            }
            out.add(frame);
        }
        return out;
    }

    /** 按模式生成帧序列；NONE 返回空列表。 */
    public static List<Matrix> build(Context ctx, int mode, String text, int cols, int rows,
                                     boolean bold, int step) {
        if (mode == SINGLE_CHAR) return singleChar(ctx, text, cols, rows, bold);
        if (mode == MARQUEE) return marquee(ctx, text, cols, rows, bold, step);
        return new ArrayList<Matrix>();
    }

    /** 按字切分，跳过空格与换行（它们不占帧）。 */
    private static List<String> chars(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        int i = 0;
        while (i < t.length()) {
            int cp = t.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            if (ch.trim().isEmpty()) continue;
            out.add(ch);
        }
        return out;
    }

    /** 帧 → 亮度数组（0 灭 / 255 亮），行优先，长度 cols*rows；可直接喂给平台点阵接口。 */
    public static int[] toLuma(Matrix m) {
        int[] v = new int[m.cols * m.rows];
        for (int y = 0; y < m.rows; y++) {
            for (int x = 0; x < m.cols; x++) {
                v[y * m.cols + x] = m.get(x, y) ? 255 : 0;
            }
        }
        return v;
    }
}
