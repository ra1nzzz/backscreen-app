package com.stepx.backscreen;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.ActivityInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 点阵背屏接口探测。
 *
 * 系统是否把背屏开放给第三方应用，最终要看设备上有没有可调用的组件：
 * ContentProvider / Service / BroadcastReceiver。这里直接枚举全机组件，
 * 用关键字筛出“像背屏”的条目，并把结果如实列出来——
 * 命中即为可用通道，未命中说明这一版系统还没有对第三方开放。
 */
public final class BackScreenProbe {

    /** 强特征词：出现即认为与背屏/点阵相关 */
    private static final String[] STRONG = {
            "backscreen", "back_screen", "back-screen", "backdisplay", "back_display",
            "rear_screen", "rearscreen", "reardisplay", "secondary_display", "secondarydisplay",
            "dotmatrix", "dot_matrix", "dot-matrix", "ledmatrix", "led_matrix",
            "miniled", "mini_led", "pixelscreen", "pixel_matrix", "点阵"
    };

    /** 弱特征词：需要包名里带 stepx / stepos 才算命中，避免把普通屏幕相关组件全捞进来 */
    private static final String[] WEAK = {"screen", "display", "matrix", "ledpanel", "panel", "led"};

    private static final String[] VENDOR_HINTS = {"stepx", "stepos", "stepaos", "amoo", "stepfun"};

    public static final class Entry {
        public final String packageName;
        public final String component;
        public final String type;      // provider / service / receiver / activity
        public final boolean exported;
        public final String marker;    // 命中的关键字

        Entry(String packageName, String component, String type, boolean exported, String marker) {
            this.packageName = packageName;
            this.component = component;
            this.type = type;
            this.exported = exported;
            this.marker = marker;
        }

        @Override
        public String toString() {
            return type + "  " + packageName + "/" + simpleName(component)
                    + (exported ? "  [exported]" : "  [private]") + "  ⟨" + marker + "⟩";
        }

        private static String simpleName(String full) {
            if (full == null) return "";
            int i = full.lastIndexOf('.');
            return i >= 0 ? full.substring(i + 1) : full;
        }
    }

    private BackScreenProbe() {
    }

    public static List<Entry> scan(Context ctx) {
        List<Entry> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(
                    PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                            | PackageManager.GET_RECEIVERS | PackageManager.GET_ACTIVITIES);
        } catch (Exception e) {
            return out;
        }
        if (packages == null) return out;

        for (PackageInfo pi : packages) {
            if (pi == null) continue;
            String pkg = pi.packageName == null ? "" : pi.packageName;
            String pkgLower = pkg.toLowerCase(Locale.ROOT);
            boolean vendor = containsAny(pkgLower, VENDOR_HINTS);

            if (pi.providers != null) {
                for (ProviderInfo provider : pi.providers) {
                    if (provider == null) continue;
                    String marker = match(pkg + "/" + provider.name + "/" + provider.authority, vendor);
                    if (marker != null) {
                        out.add(new Entry(pkg, provider.name, "provider", provider.exported, marker));
                    }
                }
            }
            if (pi.services != null) {
                for (ServiceInfo service : pi.services) {
                    if (service == null) continue;
                    String marker = match(pkg + "/" + service.name, vendor);
                    if (marker != null) {
                        out.add(new Entry(pkg, service.name, "service", service.exported, marker));
                    }
                }
            }
            if (pi.receivers != null) {
                for (ActivityInfo receiver : pi.receivers) {
                    if (receiver == null) continue;
                    String marker = match(pkg + "/" + receiver.name, vendor);
                    if (marker != null) {
                        out.add(new Entry(pkg, receiver.name, "receiver", receiver.exported, marker));
                    }
                }
            }
        }
        Collections.sort(out, (a, b) -> {
            if (a.type.equals(b.type)) return a.component.compareTo(b.component);
            return a.type.compareTo(b.type);
        });
        return out;
    }

    private static String match(String text, boolean vendorPackage) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String key : STRONG) {
            if (lower.contains(key)) return key;
        }
        if (vendorPackage) {
            for (String key : WEAK) {
                if (lower.contains(key)) return key;
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String[] keys) {
        for (String key : keys) {
            if (text.contains(key)) return true;
        }
        return false;
    }

    /** 生成可复制的纯文本报告。 */
    public static String report(Context ctx, List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("背屏接口探测\n");
        sb.append("设备：").append(android.os.Build.MANUFACTURER).append(" ")
                .append(android.os.Build.MODEL).append("  Android ")
                .append(android.os.Build.VERSION.RELEASE).append("\n");
        sb.append("命中组件：").append(entries.size()).append(" 个\n");
        if (entries.isEmpty()) {
            sb.append("未发现任何背屏相关组件。\n");
        } else {
            for (Entry e : entries) {
                sb.append("· ").append(e.toString()).append("\n");
            }
        }
        return sb.toString();
    }
}
