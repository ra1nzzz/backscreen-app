package com.stepx.backscreen;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 背屏输出。
 *
 * 现状说明（2026-10）：Step AOS 的点阵背屏目前只对系统服务与 Step Studio 运行时开放写入，
 * 普通第三方 APK 没有可用的公开接口。因此这里做两件真实可落地的事：
 *   1) 把当前点阵渲染成 PNG 落到相册（Pictures/BackScreen），可直接在 Studio 作品或系统背屏自定义里使用；
 *   2) 提供分享 Intent，把图片发给其他应用。
 * 若后续系统开放通道，只需在这里补一个写入实现，其余部分不用改。
 */
public final class BackScreenSink {

    public static final class Result {
        public boolean ok;
        public String message;
        public Uri uri;
    }

    private BackScreenSink() {
    }

    public static Bitmap render(Matrix m, int cellPx, int onColor) {
        int gap = Math.max(1, cellPx / 4);
        Bitmap bmp = Bitmap.createBitmap(m.cols * cellPx, m.rows * cellPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF050608);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int y = 0; y < m.rows; y++) {
            for (int x = 0; x < m.cols; x++) {
                float cx = x * cellPx + cellPx / 2f;
                float cy = y * cellPx + cellPx / 2f;
                if (m.get(x, y)) {
                    p.setColor(onColor);
                    p.setAlpha(50);
                    c.drawCircle(cx, cy, cellPx * 0.62f, p);
                    p.setAlpha(255);
                } else {
                    p.setColor(0xFF1A1C20);
                }
                c.drawCircle(cx, cy, (cellPx - gap) / 2f, p);
            }
        }
        return bmp;
    }

    public static Result exportPng(Context ctx, Matrix m, String baseName) {
        Result r = new Result();
        String name = baseName + "-" + System.currentTimeMillis() + ".png";
        Bitmap bmp = render(m, 20, 0xFFFF7A1A);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BackScreen");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("无法创建相册条目");
                OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                if (os != null) os.close();
                v.clear();
                v.put(MediaStore.Images.Media.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, v, null, null);
                r.ok = true;
                r.uri = uri;
                r.message = "已保存到相册 Pictures/BackScreen";
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "BackScreen");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建目录");
                File f = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                r.ok = true;
                r.uri = Uri.fromFile(f);
                r.message = "已保存到相册 BackScreen/" + name;
            }
        } catch (Exception e) {
            r.ok = false;
            r.message = "导出失败：" + e.getMessage();
        } finally {
            bmp.recycle();
        }
        return r;
    }

    public static void share(Context ctx, Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(i, "分享点阵图"));
    }
}
