package com.alfread.floatspace.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class EdgeHandleView extends View {
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean left;
    private final int accent;

    public EdgeHandleView(Context context, boolean left, int accent) {
        super(context);
        this.left = left;
        this.accent = accent;
        setFocusable(true);
        setContentDescription("FloatSpace edge handle");
        setOnClickListener(v -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float top = h * 0.06f;
        float bottom = h * 0.94f;
        float half = Math.max(1f, w * 0.42f);
        rect.set(cx - half, top, cx + half, bottom);

        glow.setShader(new LinearGradient(0, top, 0, bottom,
                0x00FFFFFF, 0xC8FFFFFF, Shader.TileMode.CLAMP));
        glow.setStyle(Paint.Style.FILL);
        glow.setShadowLayer(Math.max(5, w * 1.8f), left ? 2 : -2, 0, accent | 0x66000000);
        canvas.drawRoundRect(rect, w, w, glow);
        glow.clearShadowLayer();

        line.setColor(0xF2FFFFFF);
        line.setStyle(Paint.Style.FILL);
        RectF core = new RectF(cx - Math.max(1f, w * 0.22f), top + h * 0.08f,
                cx + Math.max(1f, w * 0.22f), bottom - h * 0.08f);
        canvas.drawRoundRect(core, w, w, line);
    }
}
