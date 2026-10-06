package com.alfread.alfvision;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.util.Base64;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final int NOTIFICATION_ID = 4201;
    private static final String CHANNEL_ID = "alf_vision";

    private WindowManager wm;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private FrameLayout root;
    private SelectionView selection;
    private LinearLayout panel;
    private TextView chat;
    private EditText question;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int screenW;
    private int screenH;
    private int density;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetricsCompat metrics = new DisplayMetricsCompat(getResources().getDisplayMetrics());
        screenW = metrics.width;
        screenH = metrics.height;
        density = metrics.density;
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_RESULT_DATA) && projection == null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (data != null) {
                startProjection(resultCode, data);
                createOverlay();
            }
        }
        return START_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(resultCode, data);

        imageReader = ImageReader.newInstance(
                screenW, screenH,
                PixelFormat.RGBA_8888,
                2
        );

        virtualDisplay = projection.createVirtualDisplay(
                "ALF Vision Capture",
                screenW,
                screenH,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );
    }

    private void createOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        root = new FrameLayout(this);
        selection = new SelectionView(this);
        root.addView(selection, new FrameLayout.LayoutParams(-1, -1));

        panel = buildPanel();
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(330), dp(500), Gravity.END | Gravity.CENTER_VERTICAL);
        panelLp.rightMargin = dp(10);
        root.addView(panel, panelLp);

        panel.setVisibility(View.GONE);

        TextView bubble = new TextView(this);
        bubble.setText("A");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(18);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.rgb(139, 92, 246));
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubble.setBackground(bubbleBg);

        FrameLayout.LayoutParams bubbleLp =
                new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END | Gravity.CENTER_VERTICAL);
        bubbleLp.rightMargin = dp(12);
        root.addView(bubble, bubbleLp);

        bubble.setOnClickListener(v -> {
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        wm.addView(root, params);
    }

    private LinearLayout buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(18, 18, 24));
        bg.setCornerRadius(dp(20));
        box.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = label("ALF Vision", 18, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button select = smallButton("Area");
        select.setOnClickListener(v -> {
            selection.setVisibility(View.VISIBLE);
            selection.mode = SelectionView.MODE_EDIT;
            selection.invalidate();
        });
        header.addView(select, new LinearLayout.LayoutParams(dp(62), dp(42)));

        Button lock = smallButton("Kunci");
        lock.setOnClickListener(v -> {
            selection.mode = SelectionView.MODE_LOCKED;
            selection.setVisibility(View.GONE);
        });
        header.addView(lock, new LinearLayout.LayoutParams(dp(62), dp(42)));

        Button hide = smallButton("Tutup");
        hide.setOnClickListener(v -> box.setVisibility(View.GONE));
        header.addView(hide, new LinearLayout.LayoutParams(dp(65), dp(42)));

        box.addView(header);

        chat = label(
                "Pilih area layar lalu tanyakan sesuatu.\n\n"
                        + "Contoh:\n"
                        + "• Ini error apa?\n"
                        + "• Jelaskan layar ini.\n"
                        + "• Apa yang harus gua pencet?",
                13, Color.LTGRAY);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(chat);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        question = new EditText(this);
        question.setHint("Tanya AI tentang area layar...");
        question.setTextColor(Color.WHITE);
        question.setHintTextColor(Color.GRAY);
        question.setSingleLine(false);
        question.setMaxLines(3);
        question.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.rgb(30, 30, 38));
        inputBg.setCornerRadius(dp(12));
        question.setBackground(inputBg);
        box.addView(question, new LinearLayout.LayoutParams(-1, dp(60)));

        Button ask = smallButton("Analisis Layar");
        ask.setOnClickListener(v -> askAI());
        LinearLayout.LayoutParams askLp = new LinearLayout.LayoutParams(-1, dp(48));
        askLp.topMargin = dp(8);
        box.addView(ask, askLp);

        return box;
    }

    private void askAI() {
        String q = question.getText().toString().trim();
        if (q.isEmpty()) q = "Jelaskan apa yang terlihat di area layar ini dan apa yang sebaiknya saya lakukan.";

        final String finalQ = q;
        appendChat("\n\nAnda: " + q + "\n\nALF: menganalisis...");

        executor.execute(() -> {
            try {
                Bitmap screenshot = captureBitmap();
                if (screenshot == null) throw new Exception("Screenshot belum tersedia.");
                Bitmap crop = cropSelection(screenshot);
                String base64 = bitmapBase64(crop);

                String answer = callGroq(finalQ, base64);
                runOnMain(() -> {
                    appendChat("\n" + answer);
                });
            } catch (Exception e) {
                runOnMain(() -> appendChat("\nGagal: " + e.getMessage()));
            }
        });
    }

    private Bitmap captureBitmap() throws Exception {
        if (imageReader == null) throw new Exception("Screen capture belum aktif.");

        long deadline = System.currentTimeMillis() + 3000;
        Image image = null;

        while (System.currentTimeMillis() < deadline) {
            image = imageReader.acquireLatestImage();
            if (image != null) break;
            SystemClock.sleep(80);
        }

        if (image == null) throw new Exception("Tidak mendapatkan frame layar.");

        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * screenW;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenW + rowPadding / pixelStride,
                    screenH,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, screenW, screenH);
            if (cropped != bitmap) bitmap.recycle();
            return cropped;
        } finally {
            image.close();
        }
    }

    private Bitmap cropSelection(Bitmap source) {
        Rect r = selection.getSelectionRect();
        int left = Math.max(0, Math.min(r.left, source.getWidth() - 1));
        int top = Math.max(0, Math.min(r.top, source.getHeight() - 1));
        int right = Math.max(left + 1, Math.min(r.right, source.getWidth()));
        int bottom = Math.max(top + 1, Math.min(r.bottom, source.getHeight()));

        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    private String bitmapBase64(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
        bitmap.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private String callGroq(String questionText, String imageBase64) throws Exception {
        String key = SecureStore.get(this);
        if (key.isEmpty()) throw new Exception("Groq API key belum disimpan.");

        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String model = prefs.getString("model", "qwen/qwen3.8-27b");
        String system = prefs.getString("prompt",
                "Kamu adalah ALF Vision, asisten AI yang melihat screenshot area layar.");

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_completion_tokens", 1200);

        JSONArray messages = new JSONArray();

        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", system);
        messages.put(sys);

        JSONArray content = new JSONArray();

        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", questionText);
        content.put(text);

        JSONObject image = new JSONObject();
        image.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/jpeg;base64," + imageBase64);
        image.put("image_url", imageUrl);
        content.put(image);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", content);
        messages.put(user);

        body.put("messages", messages);

        URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(request);

        int code = connection.getResponseCode();
        java.io.InputStream stream =
                code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        String response = readStream(stream);

        if (code < 200 || code >= 300) {
            try {
                JSONObject err = new JSONObject(response);
                JSONObject error = err.optJSONObject("error");
                String msg = error != null ? error.optString("message", response) : response;
                throw new Exception("HTTP " + code + ": " + msg);
            } catch (org.json.JSONException ignored) {
                throw new Exception("HTTP " + code + ": " + response);
            }
        }

        JSONObject json = new JSONObject(response);
        JSONArray choices = json.getJSONArray("choices");
        return choices.getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "AI tidak mengembalikan jawaban.");
    }

    private String readStream(java.io.InputStream stream) throws Exception {
        if (stream == null) return "";
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();
        return result.toString();
    }

    private void appendChat(String value) {
        runOnMain(() -> {
            if (chat != null) {
                chat.append(value);
            }
        });
    }

    private void runOnMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        return t;
    }

    private Button smallButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(52, 42, 78));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.alfread.alfvision.R.drawable.ic_alf_logo)
                .setContentTitle("ALF Vision aktif")
                .setContentText("Floating AI siap menganalisis area layar.")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "ALF Vision",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Screen capture untuk ALF Vision Panel");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        executor.shutdownNow();

        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (projection != null) projection.stop();

        if (root != null && root.getParent() != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }

    private class SelectionView extends View {
        static final int MODE_EDIT = 1;
        static final int MODE_LOCKED = 2;
        int mode = MODE_EDIT;

        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);

        private RectF rect;
        private float downX, downY;
        private int action = 0;

        SelectionView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(2));
            border.setColor(Color.rgb(139, 92, 246));

            shade.setStyle(Paint.Style.FILL);
            shade.setColor(Color.argb(105, 0, 0, 0));

            handle.setStyle(Paint.Style.FILL);
            handle.setColor(Color.WHITE);

            rect = new RectF(
                    dp(30),
                    dp(180),
                    Math.max(dp(330), screenW - dp(30)),
                    Math.min(screenH - dp(100), dp(650))
            );
        }

        Rect getSelectionRect() {
            return new Rect(
                    Math.round(rect.left),
                    Math.round(rect.top),
                    Math.round(rect.right),
                    Math.round(rect.bottom)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            canvas.drawRect(0, 0, getWidth(), rect.top, shade);
            canvas.drawRect(0, rect.bottom, getWidth(), getHeight(), shade);
            canvas.drawRect(0, rect.top, rect.left, rect.bottom, shade);
            canvas.drawRect(rect.right, rect.top, getWidth(), rect.bottom, shade);

            canvas.drawRoundRect(rect, dp(8), dp(8), border);

            float s = dp(16);
            canvas.drawRect(rect.left - s / 2, rect.top - s / 2,
                    rect.left + s / 2, rect.top + s / 2, handle);
            canvas.drawRect(rect.right - s / 2, rect.top - s / 2,
                    rect.right + s / 2, rect.top + s / 2, handle);
            canvas.drawRect(rect.left - s / 2, rect.bottom - s / 2,
                    rect.left + s / 2, rect.bottom + s / 2, handle);
            canvas.drawRect(rect.right - s / 2, rect.bottom - s / 2,
                    rect.right + s / 2, rect.bottom + s / 2, handle);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (mode != MODE_EDIT) return false;

            float x = event.getX();
            float y = event.getY();

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = x;
                downY = y;
                action = hitCorner(x, y);
                if (action == 0 && rect.contains(x, y)) action = 5;
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = x - downX;
                float dy = y - downY;

                if (action == 5) {
                    rect.offset(dx, dy);
                } else if (action == 1) {
                    rect.left += dx;
                    rect.top += dy;
                } else if (action == 2) {
                    rect.right += dx;
                    rect.top += dy;
                } else if (action == 3) {
                    rect.left += dx;
                    rect.bottom += dy;
                } else if (action == 4) {
                    rect.right += dx;
                    rect.bottom += dy;
                }

                normalizeRect();
                downX = x;
                downY = y;
                invalidate();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                return true;
            }

            return true;
        }

        private int hitCorner(float x, float y) {
            float hit = dp(30);
            if (distance(x, y, rect.left, rect.top) < hit) return 1;
            if (distance(x, y, rect.right, rect.top) < hit) return 2;
            if (distance(x, y, rect.left, rect.bottom) < hit) return 3;
            if (distance(x, y, rect.right, rect.bottom) < hit) return 4;
            return 0;
        }

        private float distance(float x1, float y1, float x2, float y2) {
            return (float) Math.hypot(x1 - x2, y1 - y2);
        }

        private void normalizeRect() {
            float min = dp(100);

            if (rect.width() < min) rect.right = rect.left + min;
            if (rect.height() < min) rect.bottom = rect.top + min;

            if (rect.left < 0) {
                float d = -rect.left;
                rect.left += d; rect.right += d;
            }
            if (rect.top < 0) {
                float d = -rect.top;
                rect.top += d; rect.bottom += d;
            }
            if (rect.right > getWidth()) {
                float d = rect.right - getWidth();
                rect.left -= d; rect.right -= d;
            }
            if (rect.bottom > getHeight()) {
                float d = rect.bottom - getHeight();
                rect.top -= d; rect.bottom -= d;
            }
        }
    }

    private static class DisplayMetricsCompat {
        final int width;
        final int height;
        final int density;

        DisplayMetricsCompat(android.util.DisplayMetrics m) {
            width = m.widthPixels;
            height = m.heightPixels;
            density = m.densityDpi;
        }
    }
}
