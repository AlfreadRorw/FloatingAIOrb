package com.alfread.floatspace.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable bg(int color, int radiusDp, int strokeColor, int strokeDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    public static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextColor(color);
        tv.setTextSize(sp);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    public static Button button(Context c, String label, boolean filled) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setMinHeight(dp(c, 46));
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setBackground(bg(filled ? Color.rgb(87, 60, 141) : Color.rgb(19, 25, 35), 16,
                filled ? Color.rgb(151, 115, 226) : Color.rgb(45, 57, 73), 1, c));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        box.setBackground(bg(Color.rgb(13, 17, 24), 20, Color.rgb(36, 48, 65), 1, c));
        return box;
    }

    public static View spacer(Context c, int height) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, height)));
        return v;
    }
}
