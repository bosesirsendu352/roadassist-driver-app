package com.roadassist.driver;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small helpers to build the screens in code (no XML layouts needed). */
public final class Ui {
    private Ui() {}
    public static final int TEAL = Color.parseColor("#0F766E");
    public static final int INK = Color.parseColor("#0F172A");
    public static final int MUTED = Color.parseColor("#64748B");
    public static final int BG = Color.parseColor("#F1F5F9");
    public static final int RED = Color.parseColor("#DC2626");
    public static final int AMBER = Color.parseColor("#B45309");

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(c, 12));
        l.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static Button button(Context c, String s, int color, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 10));
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 52));
        lp.topMargin = dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }

    public static Button outline(Context c, String s, View.OnClickListener l) {
        Button b = button(c, s, Color.WHITE, l);
        b.setTextColor(TEAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(c, 1.5f), TEAL);
        g.setCornerRadius(dp(c, 10));
        b.setBackground(g);
        return b;
    }

    public static String time(long ms) {
        if (ms <= 0) return "";
        return new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    public static String status(String s) {
        if (s == null) return "";
        switch (s) {
            case "assigned": return "Assigned";
            case "in_progress": return "In progress";
            case "done": return "Done";
            case "new": return "New";
            default: return s;
        }
    }
}
