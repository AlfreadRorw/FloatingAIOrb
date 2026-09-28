package com.alfread.arachne;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String theme = "gold";
    private String style = "arachne";
    private boolean twelve = false, seconds = true, date = true, label = true, dim = false;
    private EditText customHex;

    private final int BG = Color.rgb(5, 6, 10);
    private final int PANEL = Color.rgb(15, 17, 23);
    private final int TEXT = Color.rgb(238, 238, 242);
    private final int MUTED = Color.rgb(145, 148, 158);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        setResult(RESULT_CANCELED);
        buildUi();
    }

    private TextView title(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(18);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(4, 18, 4, 8);
        return v;
    }

    private TextView sub(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(MUTED);
        v.setTextSize(12);
        v.setPadding(4, 0, 4, 10);
        return v;
    }

    private Button chip(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setMinHeight(44);
        b.setPadding(12, 0, 12, 0);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 14, 22, 28);
        scroll.addView(root);

        TextView head = new TextView(this);
        head.setText("ARACHNE WIDGET");
        head.setTextColor(TEXT);
        head.setTextSize(25);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(head);
        root.addView(sub("Bikin widget jam lu sendiri. Setiap widget bisa punya konfigurasi berbeda."));

        root.addView(title("THEME"));
        root.addView(sub("Warna aksen dan ring widget."));
        LinearLayout themes = new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.setGravity(Gravity.CENTER);
        String[][] themeData = {
                {"GOLD", "gold"}, {"VIOLET", "violet"}, {"CYAN", "cyan"},
                {"CRIMSON", "crimson"}, {"EMERALD", "emerald"}, {"ICE", "ice"}
        };
        for (String[] t : themeData) {
            Button b = chip(t[0]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 48, 1f);
            lp.setMargins(3, 0, 3, 0);
            themes.addView(b, lp);
            b.setOnClickListener(v -> { theme = t[1]; refreshButtonStates(themes); });
            b.setTag(t[1]);
        }
        root.addView(themes);

        customHex = new EditText(this);
        customHex.setHint("Custom HEX, contoh #9B7BFF");
        customHex.setSingleLine(true);
        customHex.setTextColor(TEXT);
        customHex.setHintTextColor(MUTED);
        customHex.setTextSize(13);
        customHex.setPadding(12, 0, 12, 0);
        root.addView(customHex, new LinearLayout.LayoutParams(-1, 48));

        root.addView(title("STYLE"));
        root.addView(sub("Pilih karakter visual widget."));
        RadioGroup styles = new RadioGroup(this);
        styles.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"ARACHNE — ring + spider core", "MINIMAL — clean digital", "TERMINAL — technical HUD"};
        String[] values = {"arachne", "minimal", "terminal"};
        for (int i = 0; i < names.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(names[i]); r.setTextColor(TEXT); r.setTextSize(14); r.setTag(values[i]);
            styles.addView(r, new RadioGroup.LayoutParams(-1, 48));
            if (i == 0) r.setChecked(true);
        }
        styles.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton r = group.findViewById(checkedId);
            if (r != null) style = (String) r.getTag();
        });
        root.addView(styles);

        root.addView(title("DISPLAY"));
        root.addView(sub("Semua pilihan ini tersimpan khusus untuk widget ini."));
        CheckBox c12 = check("12-hour clock"); c12.setChecked(twelve); root.addView(c12);
        CheckBox cSec = check("Realtime seconds"); cSec.setChecked(seconds); root.addView(cSec);
        CheckBox cDate = check("Show date"); cDate.setChecked(date); root.addView(cDate);
        CheckBox cLabel = check("Show ARACHNE label"); cLabel.setChecked(label); root.addView(cLabel);
        CheckBox cDim = check("Cinematic dim mode"); cDim.setChecked(dim); root.addView(cDim);

        root.addView(title("SAVE"));
        Button save = chip("SAVE WIDGET");
        save.setTextSize(14); save.setBackgroundColor(Color.rgb(34, 35, 43));
        root.addView(save, new LinearLayout.LayoutParams(-1, 52));
        save.setOnClickListener(v -> {
            twelve = c12.isChecked(); seconds = cSec.isChecked(); date = cDate.isChecked();
            label = cLabel.isChecked(); dim = cDim.isChecked();
            WidgetPrefs.save(this, appWidgetId, theme, style, twelve, seconds, date, label, dim,
                    customHex == null ? "" : customHex.getText().toString().trim());
            AppWidgetManager.getInstance(this).updateAppWidget(appWidgetId,
                    ClockWidgetProvider.buildViews(this, appWidgetId));
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
            finish();
        });

        setContentView(scroll);
        refreshButtonStates(themes);
    }

    private CheckBox check(String text) {
        CheckBox c = new CheckBox(this);
        c.setText(text); c.setTextColor(TEXT); c.setTextSize(14);
        c.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(232, 198, 106), Color.rgb(100, 102, 112)}));
        return c;
    }

    private void refreshButtonStates(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) {
                String value = (String) v.getTag();
                v.setBackgroundColor(value != null && value.equals(theme)
                        ? Color.rgb(65, 62, 44) : Color.rgb(27, 29, 37));
            }
        }
    }
}
