package com.stepx.backscreen;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码能力：解码（相册/拍照图片）与按背屏点阵规格重新生成。
 *
 * 点阵屏上模块数受屏宽限制，所以这里不复用原图尺寸，
 * 一律按目标矩阵重新编码，选择「装得下且纠错等级尽可能高」的版本。
 */
public final class QrUtil {

    private QrUtil() {
    }

    public static final class Encoded {
        public final Matrix matrix;
        public final int moduleCount;   // 含静区前的模块数
        public final int quietZone;     // 实际使用的静区模块数
        public final ErrorCorrectionLevel level;
        public final boolean fits;

        Encoded(Matrix matrix, int moduleCount, int quietZone, ErrorCorrectionLevel level, boolean fits) {
            this.matrix = matrix;
            this.moduleCount = moduleCount;
            this.quietZone = quietZone;
            this.level = level;
            this.fits = fits;
        }
    }

    /** 可用的较高纠错等级（点阵屏有缝隙，M 及以上更稳，但内容长时自动降级） */
    private static final ErrorCorrectionLevel[] PREFERRED = {
            ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L
    };

    /**
     * 按目标点阵规格生成二维码。
     *
     * @param content 二维码内容（建议 200 字以内，点阵屏容量有限）
     */
    public static Encoded encode(String content, int cols, int rows) {
        String text = content == null ? "" : content.trim();
        Matrix fallback = new Matrix(cols, rows);
        if (text.isEmpty()) {
            return new Encoded(fallback, 0, 0, ErrorCorrectionLevel.M, false);
        }
        int side = Math.min(cols, rows);

        // 优先 H -> L 尝试，保留最大静区（4 模块）以提升扫码成功率
        for (ErrorCorrectionLevel level : PREFERRED) {
            QRCode code;
            try {
                Map<com.google.zxing.EncodeHintType, Object> hints =
                        new EnumMap<>(com.google.zxing.EncodeHintType.class);
                hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(com.google.zxing.EncodeHintType.MARGIN, 0);
                code = Encoder.encode(text, level, hints);
            } catch (Exception e) {
                continue;
            }
            ByteMatrix bm = code.getMatrix();
            int modules = bm.getWidth();

            for (int quiet : new int[]{4, 3, 2, 1}) {
                if (modules + quiet * 2 <= side) {
                    return new Encoded(render(bm, cols, rows, quiet), modules, quiet, level, true);
                }
            }
            // 该等级放不下，继续尝试更低纠错等级
        }

        // 所有等级都放不下：用最低等级 + 最小静区，尽量渲染出可辨认的部分
        try {
            Map<com.google.zxing.EncodeHintType, Object> hints =
                    new EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            QRCode code = Encoder.encode(text, ErrorCorrectionLevel.L, hints);
            ByteMatrix bm = code.getMatrix();
            return new Encoded(render(bm, cols, rows, 0), bm.getWidth(), 0, ErrorCorrectionLevel.L, false);
        } catch (Exception e) {
            return new Encoded(fallback, 0, 0, ErrorCorrectionLevel.L, false);
        }
    }

    private static Matrix render(ByteMatrix bm, int cols, int rows, int quiet) {
        Matrix m = new Matrix(cols, rows);
        int modules = bm.getWidth();
        int total = modules + quiet * 2;
        int ox = (cols - total) / 2 + quiet;
        int oy = (rows - total) / 2 + quiet;
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                if (bm.get(x, y) == 1) {
                    m.set(ox + x, oy + y, true);
                }
            }
        }
        return m;
    }

    /** 从任意图片解码二维码内容；失败返回 null。 */
    public static Result decode(Bitmap bitmap) {
        if (bitmap == null) return null;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int maxSide = 1400;
        if (Math.max(w, h) > maxSide) {
            float scale = maxSide / (float) Math.max(w, h);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, (int) (w * scale)),
                    Math.max(1, (int) (h * scale)), true);
            bitmap = scaled;
            w = bitmap.getWidth();
            h = bitmap.getHeight();
        }
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        MultiFormatReader reader = new MultiFormatReader();
        try {
            reader.setHints(hints);
            return reader.decodeWithState(new BinaryBitmap(
                    new HybridBinarizer(new RGBLuminanceSource(w, h, pixels))));
        } catch (Exception e) {
            return null;
        } finally {
            reader.reset();
        }
    }
}
