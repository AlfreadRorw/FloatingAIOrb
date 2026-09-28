package com.alfread.floatspace.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import com.alfread.floatspace.MainActivity;
import com.alfread.floatspace.R;
import com.alfread.floatspace.model.AppInfo;
import com.alfread.floatspace.model.AppRepository;
import com.alfread.floatspace.shizuku.ShizukuBridge;
import com.alfread.floatspace.shizuku.WindowController;
import com.alfread.floatspace.util.Prefs;
import com.alfread.floatspace.util.Ui;

import java.util.ArrayList;
import java.util.List;

public final class AppDrawerOverlay {
    private final Context serviceContext;
    private final WindowManager wm;
    private final Context overlayContext;
    private final Runnable onDismiss;
    private final AppRepository repo;
    private View root;
    private EditText search;
    private LinearLayout list;
    private boolean favoritesOnly = false;
    private List<AppInfo> allApps = new ArrayList<>();
    private WindowManager.LayoutParams params;

    public AppDrawerOverlay(Context serviceContext, WindowManager wm, Context overlayContext, Runnable onDismiss) {
        this.serviceContext = serviceContext;
        this.wm = wm;
        this.overlayContext = overlayContext;
        this.onDismiss = onDismiss;
        repo = new AppRepository(serviceContext);
    }

    public void show() {
        root = build();
        params = new WindowManager.LayoutParams(
                Ui.dp(overlayContext, 360),
                Ui.dp(overlayContext, 660),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.graphics.PixelFormat.TRANSLUCENT);
        boolean leftEdge = "left".equals(Prefs.get(serviceContext).getString(Prefs.EDGE_SIDE, "right"));
        params.gravity = (leftEdge ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        params.x = Ui.dp(overlayContext, 12);
        params.dimAmount = 0.12f;
        try {
            wm.addView(root, params);
            root.setAlpha(0f);
            root.setTranslationX(Ui.dp(overlayContext, 42));
            root.animate().alpha(1f).translationX(0).setDuration(220).start();
        } catch (Exception e) {
            Toast.makeText(serviceContext, "Tidak bisa membuka panel", Toast.LENGTH_SHORT).show();
        }
    }

    public void dismiss() {
        if (root == null) return;
        try {
            root.animate().alpha(0f).translationX(Ui.dp(overlayContext, 30)).setDuration(160)
                    .withEndAction(() -> {
                        try { wm.removeView(root); } catch (Exception ignored) {}
                        root = null;
                        onDismiss.run();
                    }).start();
        } catch (Exception e) {
            try { wm.removeViewImmediate(root); } catch (Exception ignored) {}
            root = null;
            onDismiss.run();
        }
    }

    private View build() {
        LinearLayout card = new LinearLayout(overlayContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(overlayContext, 16), Ui.dp(overlayContext, 14),
                Ui.dp(overlayContext, 16), Ui.dp(overlayContext, 12));
        card.setBackground(Ui.bg(0xF20D1118, 26, 0xFF2A3748, 1, overlayContext));
        card.setElevation(Ui.dp(overlayContext, 18));

        LinearLayout header = new LinearLayout(overlayContext);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(overlayContext, "FLOATSPACE", 17, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(overlayContext, 42), 1));
        TextView close = Ui.text(overlayContext, "×", 28, 0xFFB9C4D3, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(overlayContext, 40), Ui.dp(overlayContext, 42)));
        card.addView(header);

        TextView subtitle = Ui.text(overlayContext,
                ShizukuBridge.available() ? "FREEFORM ENGINE • READY" : "FREEFORM ENGINE • SHIZUKU OFF",
                10, ShizukuBridge.available() ? 0xFF78E5B0 : 0xFFFF7F96, true);
        subtitle.setLetterSpacing(0.13f);
        card.addView(subtitle, new LinearLayout.LayoutParams(-1, Ui.dp(overlayContext, 20)));

        search = new EditText(overlayContext);
        search.setSingleLine(true);
        search.setHint("Cari aplikasi...");
        search.setHintTextColor(0xFF6D7785);
        search.setTextColor(Color.WHITE);
        search.setTextSize(14);
        search.setPadding(Ui.dp(overlayContext, 14), 0, Ui.dp(overlayContext, 14), 0);
        search.setBackground(Ui.bg(0xFF111822, 16, 0xFF243041, 1, overlayContext));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(overlayContext, 48));
        sp.topMargin = Ui.dp(overlayContext, 10);
        card.addView(search, sp);

        LinearLayout tabs = new LinearLayout(overlayContext);
        tabs.setPadding(0, Ui.dp(overlayContext, 10), 0, Ui.dp(overlayContext, 4));
        TextView fav = tab("PINNED");
        TextView all = tab("ALL APPS");
        fav.setOnClickListener(v -> { favoritesOnly = true; refresh(); });
        all.setOnClickListener(v -> { favoritesOnly = false; refresh(); });
        tabs.addView(fav, new LinearLayout.LayoutParams(0, Ui.dp(overlayContext, 36), 1));
        tabs.addView(all, new LinearLayout.LayoutParams(0, Ui.dp(overlayContext, 36), 1));
        card.addView(tabs);

        View workspace = workspaceControls();
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, Ui.dp(overlayContext, 148));
        wp.topMargin = Ui.dp(overlayContext, 4);
        card.addView(workspace, wp);

        ScrollView scroll = new ScrollView(overlayContext);
        scroll.setFillViewport(true);
        list = new LinearLayout(overlayContext);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.topMargin = Ui.dp(overlayContext, 5);
        card.addView(scroll, lp);

        LinearLayout footer = new LinearLayout(overlayContext);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView settings = Ui.text(overlayContext, "SETTINGS", 11, 0xFF9AA6B5, true);
        settings.setLetterSpacing(0.1f);
        settings.setPadding(Ui.dp(overlayContext, 4), 0, 0, 0);
        settings.setOnClickListener(v -> {
            IntentHelper.open(serviceContext, MainActivity.class);
            dismiss();
        });
        footer.addView(settings, new LinearLayout.LayoutParams(0, Ui.dp(overlayContext, 44), 1));
        TextView info = Ui.text(overlayContext, "v1.0", 10, 0xFF566171, false);
        footer.addView(info, new LinearLayout.LayoutParams(Ui.dp(overlayContext, 45), Ui.dp(44)));
        card.addView(footer);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });

        allApps = repo.getApps();
        refresh();
        return card;
    }

    private TextView tab(String label) {
        TextView t = Ui.text(overlayContext, label, 10, 0xFF8793A2, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.bg(0xFF101720, 12, 0xFF243041, 1, overlayContext));
        return t;
    }

    private View workspaceControls() {
        LinearLayout box = new LinearLayout(overlayContext);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(overlayContext, 12), Ui.dp(overlayContext, 9), Ui.dp(overlayContext, 12), Ui.dp(overlayContext, 9));
        box.setBackground(Ui.bg(0xFF0A0F15, 17, 0xFF202B39, 1, overlayContext));

        WindowController.WindowState last = WindowController.last();
        String label = last == null ? "NO FLOATING WINDOW" : last.packageName;
        TextView title = Ui.text(overlayContext, "WORKSPACE  •  " + label, 9, 0xFF6F7B89, true);
        title.setLetterSpacing(0.09f);
        box.addView(title, new LinearLayout.LayoutParams(-1, Ui.dp(22)));

        LinearLayout top = new LinearLayout(overlayContext);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String[] primary = {"FOCUS", "−", "+", "CLOSE"};
        for (String action : primary) {
            TextView b = smallButton(action);
            b.setOnClickListener(v -> {
                WindowController.WindowState state = WindowController.last();
                if (state == null) return;
                switch (action) {
                    case "FOCUS": WindowController.focus(serviceContext, state.packageName); break;
                    case "−": WindowController.resize(serviceContext, state.packageName, 0.82f); break;
                    case "+": WindowController.resize(serviceContext, state.packageName, 1.22f); break;
                    case "CLOSE": WindowController.close(serviceContext, state.packageName); break;
                }
            });
            top.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(40), 1));
        }
        box.addView(top, new LinearLayout.LayoutParams(-1, Ui.dp(42)));

        LinearLayout move = new LinearLayout(overlayContext);
        move.setGravity(Gravity.CENTER);
        TextView left = smallButton("←");
        TextView drag = smallButton("DRAG");
        TextView right = smallButton("→");
        move.addView(left, new LinearLayout.LayoutParams(Ui.dp(54), Ui.dp(38)));
        move.addView(drag, new LinearLayout.LayoutParams(0, Ui.dp(38), 1));
        move.addView(right, new LinearLayout.LayoutParams(Ui.dp(54), Ui.dp(38)));
        left.setOnClickListener(v -> {
            WindowController.WindowState state = WindowController.last();
            if (state != null) WindowController.move(serviceContext, state.packageName, -80, 0);
        });
        right.setOnClickListener(v -> {
            WindowController.WindowState state = WindowController.last();
            if (state != null) WindowController.move(serviceContext, state.packageName, 80, 0);
        });
        final float[] down = new float[2];
        drag.setOnTouchListener((v, event) -> {
            WindowController.WindowState state = WindowController.last();
            if (state == null) return false;
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX();
                down[1] = event.getRawY();
                return true;
            }
            if (event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - down[0];
                float dy = event.getRawY() - down[1];
                if (Math.abs(dx) >= 8 || Math.abs(dy) >= 8) {
                    WindowController.move(serviceContext, state.packageName, Math.round(dx), Math.round(dy));
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                }
                return true;
            }
            return true;
        });
        box.addView(move, new LinearLayout.LayoutParams(-1, Ui.dp(40)));
        return box;
    }

    private TextView smallButton(String label) {
        TextView b = Ui.text(overlayContext, label, 10, 0xFFE7ECF4, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.bg(0xFF131B25, 11, 0xFF2B394B, 1, overlayContext));
        return b;
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        int shown = 0;
        for (AppInfo app : allApps) {
            if (favoritesOnly && !repo.isFavorite(app.packageName)) continue;
            if (!q.isEmpty() && !app.label.toLowerCase().contains(q) && !app.packageName.toLowerCase().contains(q)) continue;
            list.addView(row(app));
            shown++;
        }
        if (shown == 0) {
            TextView empty = Ui.text(overlayContext,
                    favoritesOnly ? "Belum ada aplikasi yang dipin." : "Tidak ada aplikasi yang cocok.",
                    12, 0xFF7C8898, false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, Ui.dp(90)));
        }
    }

    private View row(AppInfo app) {
        LinearLayout row = new LinearLayout(overlayContext);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(overlayContext, 8), Ui.dp(overlayContext, 6), Ui.dp(overlayContext, 8), Ui.dp(overlayContext, 6));
        row.setBackground(Ui.bg(0xFF0F151D, 15, 0xFF1C2735, 1, overlayContext));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, Ui.dp(58));
        rp.bottomMargin = Ui.dp(overlayContext, 7);
        row.setLayoutParams(rp);

        ImageView icon = new ImageView(overlayContext);
        icon.setImageDrawable(app.icon);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));

        LinearLayout center = new LinearLayout(overlayContext);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(Ui.dp(overlayContext, 10), 0, Ui.dp(overlayContext, 6), 0);
        TextView name = Ui.text(overlayContext, app.label, 13, Color.WHITE, true);
        TextView pkg = Ui.text(overlayContext, app.packageName, 9, 0xFF687687, false);
        center.addView(name, new LinearLayout.LayoutParams(-1, Ui.dp(24)));
        center.addView(pkg, new LinearLayout.LayoutParams(-1, Ui.dp(18)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        row.addView(center, cp);

        TextView star = Ui.text(overlayContext, repo.isFavorite(app.packageName) ? "★" : "☆", 20,
                repo.isFavorite(app.packageName) ? 0xFFB58CFF : 0xFF687687, false);
        star.setGravity(Gravity.CENTER);
        star.setOnClickListener(v -> {
            repo.toggleFavorite(app.packageName);
            refresh();
        });
        row.addView(star, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(44)));

        row.setOnClickListener(v -> {
            boolean force = Prefs.get(serviceContext).getBoolean(Prefs.FORCE_RESIZABLE, true);
            WindowController.launch(serviceContext, app, force);
            dismiss();
        });
        row.setOnLongClickListener(v -> {
            repo.toggleFavorite(app.packageName);
            refresh();
            Toast.makeText(serviceContext,
                    repo.isFavorite(app.packageName) ? "Pinned: " + app.label : "Unpinned: " + app.label,
                    Toast.LENGTH_SHORT).show();
            return true;
        });
        return row;
    }

    private static final class IntentHelper {
        static void open(Context c, Class<?> target) {
            android.content.Intent i = new android.content.Intent(c, target);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        }
    }
}
