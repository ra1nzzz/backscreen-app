package com.stepx.backscreen;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 统一的浅色深底视觉与控件工厂（不依赖 AndroidX）。 */
public final class Ui {

    public static final int BG = 0xFF0B0C0E;
    public static final int CARD = 0xFF141619;
    public static final int CARD_2 = 0xFF1B1E23;
    public static final int TEXT = 0xFFF2F3F5;
    public static final int MUTED = 0xFF8A8F98;
    public static final int ACCENT = 0xFFFF7A1A;

    private Ui() {
    }

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(Context c, float radiusDp, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, radiusDp));
        d.setColor(fill);
        return d;
    }

    public static GradientDrawable roundStroke(Context c, float radiusDp, int fill, int strokeColor, float strokeDp) {
        GradientDrawable d = round(c, radiusDp, fill);
        d.setStroke((int) dp(c, strokeDp), strokeColor);
        return d;
    }

    public static TextView label(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(primary ? 0xFF160B00 : TEXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding((int) dp(c, 16), (int) dp(c, 11), (int) dp(c, 16), (int) dp(c, 11));
        b.setBackground(primary ? round(c, 12, ACCENT) : roundStroke(c, 12, CARD_2, 0xFF2A2E35, 1));
        b.setStateListAnimator(null);
        return b;
    }

    public static LinearLayout row(Context c, int gapDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (gapDp > 0) l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static Space gap(Context c, int heightDp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) dp(c, heightDp)));
        return s;
    }

    public static void weight(View v, float w) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).weight = w;
            ((LinearLayout.LayoutParams) lp).width = 0;
            v.setLayoutParams(lp);
        }
    }

    static class Space extends View {
        Space(Context c) {
            super(c);
        }
    }
}
