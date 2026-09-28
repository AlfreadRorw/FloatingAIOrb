package com.alfread.arachne;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private FrameLayout content;
    private View splash;
    private boolean introFinished = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(3, 4, 8));

        content = new FrameLayout(this);
        content.setBackgroundColor(Color.rgb(3, 4, 8));
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(3, 4, 8));
        web.setAlpha(0f);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                playIntro();
            }
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        content.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }

            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) content.getLayoutParams();
            cp.topMargin = top;
            cp.bottomMargin = bottom;
            content.setLayoutParams(cp);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
        web.loadUrl("file:///android_asset/index.html");

        // Fail-safe: the intro can never block the app forever.
        new Handler().postDelayed(this::playIntro, 1800L);
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(3, 4, 8));
        window.setNavigationBarColor(Color.rgb(3, 4, 8));
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            window.setAttributes(lp);
        }
        window.getDecorView().setSystemUiVisibility(0);
    }

    private View buildSplash() {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.rgb(3, 4, 8));
        box.setAlpha(1f);

        View glow = new View(this);
        glow.setBackgroundResource(com.alfread.arachne.R.drawable.splash_glow);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(dp(250), dp(250));
        glowLp.gravity = Gravity.CENTER;
        box.addView(glow, glowLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(com.alfread.arachne.R.drawable.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAlpha(0f);
        icon.setScaleX(0.72f);
        icon.setScaleY(0.72f);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(116), dp(116));
        iconLp.gravity = Gravity.CENTER;
        iconLp.bottomMargin = dp(42);
        box.addView(icon, iconLp);

        LinearLayoutCompatLike texts = new LinearLayoutCompatLike(this);
        texts.setGravity(Gravity.CENTER_HORIZONTAL);
        texts.setOrientation(LinearLayoutCompatLike.VERTICAL);
        texts.setAlpha(0f);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(90));
        textLp.gravity = Gravity.CENTER;
        textLp.topMargin = dp(132);
        box.addView(texts, textLp);

        TextView title = new TextView(this);
        title.setText("ARACHNE");
        title.setTextColor(Color.rgb(244, 236, 209));
        title.setTextSize(18);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        title.setLetterSpacing(0.38f);
        title.setGravity(Gravity.CENTER);
        texts.addView(title, new FrameLayout.LayoutParams(-1, dp(35)));

        TextView subtitle = new TextView(this);
        subtitle.setText("KINETIC TIMEWORKS");
        subtitle.setTextColor(Color.rgb(107, 101, 85));
        subtitle.setTextSize(8);
        subtitle.setLetterSpacing(0.30f);
        subtitle.setGravity(Gravity.CENTER);
        texts.addView(subtitle, new FrameLayout.LayoutParams(-1, dp(28)));

        TextView loading = new TextView(this);
        loading.setText("INITIALIZING");
        loading.setTextColor(Color.rgb(232, 198, 106));
        loading.setTextSize(7);
        loading.setLetterSpacing(0.32f);
        loading.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(-1, dp(25));
        loadingLp.gravity = Gravity.CENTER;
        loadingLp.topMargin = dp(205);
        box.addView(loading, loadingLp);

        box.setTag(new Object[]{icon, texts, loading});
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650L).setStartDelay(100L).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        texts.animate().alpha(1f).translationY(-4f).setDuration(650L).setStartDelay(240L).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        glow.animate().alpha(0.92f).scaleX(1.12f).scaleY(1.12f).setDuration(1100L).setStartDelay(50L).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
        loading.animate().alpha(0.35f).setDuration(550L).setStartDelay(500L).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
        icon.animate().rotation(360f).setDuration(1400L).setStartDelay(150L).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        return box;
    }

    private void playIntro() {
        if (introFinished || web == null || splash == null) return;
        introFinished = true;

        web.animate()
                .alpha(1f)
                .setDuration(520L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        splash.animate()
                .alpha(0f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setStartDelay(120L)
                .setDuration(430L)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (root != null && splash != null) root.removeView(splash);
                    splash = null;
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // Tiny vertical layout helper to keep this project dependency-light.
    private static class LinearLayoutCompatLike extends android.widget.LinearLayout {
        static final int VERTICAL = android.widget.LinearLayout.VERTICAL;
        LinearLayoutCompatLike(android.content.Context context) { super(context); }
    }
}
