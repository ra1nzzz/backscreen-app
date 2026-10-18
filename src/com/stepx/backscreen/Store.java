package com.stepx.backscreen;

import android.content.Context;
import android.content.SharedPreferences;

/** 轻量配置存储。 */
public final class Store {
    private static final String PREF = "backscreen";
    private final SharedPreferences sp;

    public Store(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public int cols() {
        return sp.getInt("cols", 32);
    }

    public int rows() {
        return sp.getInt("rows", 32);
    }

    public void setSize(int cols, int rows) {
        sp.edit().putInt("cols", cols).putInt("rows", rows).apply();
    }

    public String text() {
        return sp.getString("text", "Amoo\n42");
    }

    public void setText(String v) {
        sp.edit().putString("text", v).apply();
    }

    public String qrContent() {
        return sp.getString("qr", "https://studio.stepx.com/");
    }

    public void setQrContent(String v) {
        sp.edit().putString("qr", v).apply();
    }

    public String pixel() {
        return sp.getString("pixel", "");
    }

    public void setPixel(String v) {
        sp.edit().putString("pixel", v).apply();
    }

    public static final String DEFAULT_STATUS_URL =
            "https://raw.githubusercontent.com/ra1nzzz/amoo-status/main/status.json";

    public String statusUrl() {
        return sp.getString("status_url", DEFAULT_STATUS_URL);
    }

    public void setStatusUrl(String v) {
        sp.edit().putString("status_url", v).apply();
    }

    public int mode() {
        return sp.getInt("mode", 0); // 0=文字 1=二维码 2=像素 3=状态
    }

    public void setMode(int v) {
        sp.edit().putInt("mode", v).apply();
    }

    public boolean followStatus() {
        return sp.getBoolean("follow_status", false);
    }

    public void setFollowStatus(boolean v) {
        sp.edit().putBoolean("follow_status", v).apply();
    }

    public boolean bold() {
        return sp.getBoolean("bold", true);
    }

    public void setBold(boolean v) {
        sp.edit().putBoolean("bold", v).apply();
    }

    public String cachedStatus() {
        return sp.getString("status_cache", "");
    }

    public void setCachedStatus(String v) {
        sp.edit().putString("status_cache", v).apply();
    }

    public int threshold() {
        return sp.getInt("threshold", 128);
    }

    public void setThreshold(int v) {
        sp.edit().putInt("threshold", v).apply();
    }

    public boolean dither() {
        return sp.getBoolean("dither", false);
    }

    public void setDither(boolean v) {
        sp.edit().putBoolean("dither", v).apply();
    }
}
