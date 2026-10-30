package com.stepx.backscreen;

import android.content.ContentValues;
import android.content.Context;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
 * 现状说明（2026-10 复核）：Step AOS 的点阵背屏只对系统服务与运行时开放写入，
 * 普通第三方 APK 仍然没有可直写的公开接口；真机可用通道是平台的背屏原子能力
 * （推 17×23 / 391 格亮度帧）。因此这里提供三条落地路径：
 *   1) 导出 PNG 到相册（Pictures/BackScreen），可在系统背屏自定义或 Studio 作品里选用；
 *   2) 导出轮播帧序列 JSON 到 Download/BackScreen，字段与平台点阵接口的 frame 参数一致，可直接逐帧下发；
 *   3) 分享 Intent，把图片发给其他应用。
 * 系统若某天开放 APK 直写，只需在这里补一个写入实现，其余部分不用改。
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


    // ---------------------------------------------------------- 帧序列导出

    /**
     * 把轮播帧序列导出成 JSON，落到 Download/BackScreen。
     *
     * 结构：{"cols":17,"rows":23,"intervalMs":1200,"mode":2,"frames":[[391 个 0/255], ...]}
     * 每帧行优先、长度 cols*rows，与平台点阵接口的 frame 参数一致，可直接取用。
     */
    public static Result exportFramesJson(Context ctx, java.util.List<Matrix> frames,
                                          int intervalMs, int mode, String baseName) {
        Result r = new Result();
        if (frames == null || frames.isEmpty()) {
            r.ok = false;
            r.message = "没有可导出的帧（先在文字面板打开轮播）";
            return r;
        }
        Matrix f0 = frames.get(0);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"cols\":").append(f0.cols)
                .append(",\"rows\":").append(f0.rows)
                .append(",\"intervalMs\":").append(intervalMs)
                .append(",\"mode\":").append(mode)
                .append(",\"frames\":[");
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) sb.append(',');
            int[] luma = FrameAnim.toLuma(frames.get(i));
            sb.append('[');
            for (int k = 0; k < luma.length; k++) {
                if (k > 0) sb.append(',');
                sb.append(luma[k]);
            }
            sb.append(']');
        }
        sb.append("]}");

        String name = baseName + "-" + System.currentTimeMillis() + ".json";
        byte[] bytes = sb.toString().getBytes();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/BackScreen");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("无法创建下载条目");
                OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                if (os == null) throw new IllegalStateException("无法写入");
                os.write(bytes);
                os.close();
                v.clear();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, v, null, null);
                r.ok = true;
                r.uri = uri;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "BackScreen");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建目录");
                File f = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(bytes);
                fos.close();
                r.ok = true;
                r.uri = Uri.fromFile(f);
            }
            int n = frames.size();
            r.message = "已导出 " + n + " 帧（" + f0.cols + "×" + f0.rows + "）到 Download/BackScreen/"
                    + name;
        } catch (Exception e) {
            r.ok = false;
            r.message = "导出失败：" + e.getMessage();
        }
        return r;
    }

    // ---------------------------------------------------------- 通知镜像

    /** 把当前点阵渲染成一条带大图的通知（系统若把关键通知投到背屏，这里就能看到）。 */
    public static Result notifyPreview(Context ctx, Matrix m, String title) {
        Result r = new Result();
        try {
            Bitmap bmp = render(m, 12, 0xFFFF7A1A);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "backscreen_preview";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(channelId, "背屏预览",
                        NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new Notification.Builder(ctx, channelId);
            } else {
                b = new Notification.Builder(ctx);
            }
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText("点阵 " + m.cols + "×" + m.rows + " · " + m.onCount() + " 个亮点")
                    .setStyle(new Notification.BigPictureStyle().bigPicture(bmp))
                    .setAutoCancel(true);
            nm.notify(1001, b.build());
            bmp.recycle();
            r.ok = true;
            r.message = "已发出预览通知";
        } catch (Exception e) {
            r.ok = false;
            r.message = "通知发送失败：" + e.getMessage();
        }
        return r;
    }

    // ---------------------------------------------------------- 通道尝试

    /**
     * 对探测到的组件做一次投递尝试。
     * 说明：只能确认“已投递”，无法确认对端是否消费——真正的接口语义要等系统给出文档。
     */
    public static String tryPush(Context ctx, java.util.List<BackScreenProbe.Entry> entries, Matrix m) {
        if (entries == null || entries.isEmpty()) {
            return "没有可尝试的组件";
        }
        int sent = 0;
        StringBuilder log = new StringBuilder();
        String payload = matrixJson(m);
        for (BackScreenProbe.Entry e : entries) {
            if (!e.exported) {
                log.append("跳过私有组件 ").append(shortName(e.component)).append("\n");
                continue;
            }
            try {
                android.content.ComponentName cn = new android.content.ComponentName(e.packageName, e.component);
                Intent i = new Intent();
                i.setComponent(cn);
                i.putExtra("matrix", payload);
                i.putExtra("data", payload);
                i.putExtra("cols", m.cols);
                i.putExtra("rows", m.rows);
                if ("receiver".equals(e.type)) {
                    ctx.sendBroadcast(i);
                    sent++;
                } else if ("service".equals(e.type)) {
                    ctx.startService(i);
                    sent++;
                } else if ("provider".equals(e.type)) {
                    android.net.Uri uri = android.net.Uri.parse("content://" + e.component);
                    ctx.getContentResolver().call(uri, "setMatrix", payload, null);
                    sent++;
                }
            } catch (Exception ex) {
                log.append("失败 ").append(shortName(e.component)).append("：")
                        .append(ex.getClass().getSimpleName()).append("\n");
            }
        }
        return "已向 " + sent + " 个组件投递" + (log.length() > 0 ? "\n" + log : "");
    }

    private static String matrixJson(Matrix m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"cols\":").append(m.cols).append(",\"rows\":").append(m.rows).append(",\"cells\":[");
        for (int i = 0; i < m.cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(m.cells[i] ? 1 : 0);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String shortName(String full) {
        if (full == null) return "";
        int i = full.lastIndexOf('.');
        return i >= 0 ? full.substring(i + 1) : full;
    }

    public static void share(Context ctx, Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(i, "分享点阵图"));
    }
}
