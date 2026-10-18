package com.stepx.backscreen;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Codex 状态数据源。
 *
 * 约定一份很轻的 JSON（服务端可随时更新）：
 * {
 *   "active": 2,                     // 进行中会话数量
 *   "waiting": 0,                    // 其中等待人工回答的数量
 *   "status": "developing",          // developing | waiting | idle
 *   "detail": "ecom-after-sales-local",
 *   "updated": "2026-10-18T19:50:00+08:00"
 * }
 * status 缺省时按 active/waiting 推断。
 */
public final class StatusClient {

    public static final class Snapshot {
        public int active;
        public int waiting;
        public String state = "idle";   // developing | waiting | idle
        public String detail = "";
        public String updated = "";
        public String error = "";

        public boolean ok() {
            return error == null || error.isEmpty();
        }

        public String stateLabel() {
            if ("developing".equals(state)) return "开发中";
            if ("waiting".equals(state)) return "等待人工回答";
            return "摸鱼中";
        }

        public int glyph() {
            if ("developing".equals(state)) return 1;
            if ("waiting".equals(state)) return 2;
            return 0;
        }

        public String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("active", active);
                o.put("waiting", waiting);
                o.put("state", state);
                o.put("detail", detail);
                o.put("updated", updated);
                return o.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        public static Snapshot fromJson(String json) {
            Snapshot s = new Snapshot();
            if (json == null || json.trim().isEmpty()) {
                s.error = "暂无数据";
                return s;
            }
            try {
                JSONObject o = new JSONObject(json);
                s.active = o.optInt("active", 0);
                s.waiting = o.optInt("waiting", 0);
                s.detail = o.optString("detail", "");
                s.updated = o.optString("updated", "");
                String st = o.optString("status", "");
                if (st.isEmpty()) st = o.optString("state", "");
                if (st.isEmpty()) {
                    st = s.waiting > 0 ? "waiting" : (s.active > 0 ? "developing" : "idle");
                }
                s.state = normalize(st);
            } catch (JSONException e) {
                s.error = "数据格式错误";
            }
            return s;
        }

        private static String normalize(String raw) {
            String v = raw.toLowerCase();
            if (v.contains("wait") || v.contains("asked") || v.contains("input")) return "waiting";
            if (v.contains("dev") || v.contains("run") || v.contains("busy") || v.contains("coding")) {
                return "developing";
            }
            return "idle";
        }
    }

    /** 同步拉取，调用方需在工作线程执行。 */
    public static Snapshot fetch(String url, int timeoutMs) {
        Snapshot s = new Snapshot();
        if (url == null || url.trim().isEmpty()) {
            s.error = "未配置数据源";
            return s;
        }
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url.trim());
            if (!"https".equalsIgnoreCase(u.getProtocol()) && !"http".equalsIgnoreCase(u.getProtocol())) {
                s.error = "仅支持 http/https";
                return s;
            }
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cache-Control", "no-cache");
            int code = conn.getResponseCode();
            if (code != 200) {
                s.error = "HTTP " + code;
                return s;
            }
            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return Snapshot.fromJson(sb.toString());
        } catch (Exception e) {
            s.error = e.getClass().getSimpleName();
            return s;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
