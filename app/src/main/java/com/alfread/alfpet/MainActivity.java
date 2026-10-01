package com.alfread.alfpet;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String PREFS = "alf_pet_state";
    private PetState state;
    private PetView petView;
    private TextView statsText, levelText, eventText, coinText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastUiUpdate;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        App.ctx=this;
        getWindow().setStatusBarColor(Color.rgb(13,16,32));
        getWindow().setNavigationBarColor(Color.rgb(13,16,32));
        state = PetState.load(this);
        state.applyOfflineDecay();
        state.save(this);
        buildUi();
        startLoop();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(13,16,32));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("ALF PET", 24, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        coinText = label("Coins 100", 16, Color.rgb(230,232,245), true);
        header.addView(coinText, new LinearLayout.LayoutParams(dp(110), dp(44)));
        root.addView(header);

        levelText = label("Level 1   •   0 XP", 15, Color.rgb(169,166,205), false);
        root.addView(levelText, new LinearLayout.LayoutParams(-1, dp(28)));

        petView = new PetView(this);
        LinearLayout.LayoutParams petLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        petLp.topMargin = dp(8);
        root.addView(petView, petLp);

        TextView name = label("Lumi", 22, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        root.addView(name, new LinearLayout.LayoutParams(-1, dp(36)));

        statsText = label("", 13, Color.rgb(204,205,220), false);
        statsText.setGravity(Gravity.CENTER);
        root.addView(statsText, new LinearLayout.LayoutParams(-1, dp(54)));

        eventText = label("Lumi sedang menunggumu.", 13, Color.rgb(173,164,255), false);
        eventText.setGravity(Gravity.CENTER);
        root.addView(eventText, new LinearLayout.LayoutParams(-1, dp(35)));

        GridLayout actions = new GridLayout(this);
        actions.setColumnCount(2);
        actions.setRowCount(3);
        String[] labels = {"FEED", "PLAY", "SLEEP", "CLEAN", "PET", "RANDOM"};
        for (String s : labels) {
            TextView btn = button(s);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = dp(48); lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            actions.addView(btn, lp);
            btn.setOnClickListener(v -> act(s));
        }
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(174)));

        setContentView(root);
        updateUi();
    }

    private void act(String action) {
        switch (action) {
            case "FEED":
                if (state.coins < 5) { toast("Coin tidak cukup."); return; }
                state.coins -= 5; state.hunger = clamp(state.hunger + 22); state.happiness = clamp(state.happiness + 3); state.addXp(6);
                eventText.setText("Lumi makan dengan lahap."); break;
            case "PLAY":
                if (state.energy < 12) { toast("Lumi terlalu lelah."); return; }
                state.energy = clamp(state.energy - 12); state.hunger = clamp(state.hunger - 6); state.happiness = clamp(state.happiness + 18); state.addXp(12); state.coins += 3;
                eventText.setText("Permainan selesai. +3 coin."); break;
            case "SLEEP":
                state.energy = clamp(state.energy + 30); state.hunger = clamp(state.hunger - 4); state.happiness = clamp(state.happiness + 2); state.addXp(5);
                eventText.setText("Lumi tidur dan mengisi energi."); break;
            case "CLEAN":
                state.clean = clamp(state.clean + 30); state.happiness = clamp(state.happiness + 4); state.addXp(5);
                eventText.setText("Lumi sekarang bersih."); break;
            case "PET":
                state.happiness = clamp(state.happiness + 5); state.addXp(2);
                eventText.setText("Lumi senang dielus."); break;
            case "RANDOM":
                Random r = new Random();
                int roll = r.nextInt(4);
                if (roll == 0) { state.coins += 10; eventText.setText("Lumi menemukan 10 coin."); }
                else if (roll == 1) { state.happiness = clamp(state.happiness + 12); eventText.setText("Lumi menemukan mainan baru."); }
                else if (roll == 2) { state.hunger = clamp(state.hunger + 10); eventText.setText("Seseorang memberi Lumi makanan."); }
                else { state.energy = clamp(state.energy + 15); eventText.setText("Lumi tidur sebentar."); }
                state.addXp(8); break;
        }
        state.lastSaved = System.currentTimeMillis(); state.save(this); updateUi();
    }

    private void startLoop() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                state.tick();
                updateUi();
                handler.postDelayed(this, 30_000);
            }
        }, 30_000);
    }

    private void updateUi() {
        levelText.setText(String.format(Locale.US, "Level %d   •   %d / %d XP", state.level, state.xp, state.xpNeeded()));
        coinText.setText("Coins " + state.coins);
        statsText.setText(String.format(Locale.US, "Hunger %d%%     Happiness %d%%\nEnergy %d%%     Clean %d%%     Health %d%%", Math.round(state.hunger), Math.round(state.happiness), Math.round(state.energy), Math.round(state.clean), Math.round(state.health)));
        petView.invalidate();
    }

    private TextView label(String t, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(t); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }

    private TextView button(String t) {
        TextView v = label(t, 14, Color.WHITE, true); v.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.rgb(38, 42, 72)); gd.setCornerRadius(dp(16)); v.setBackground(gd); v.setAllCaps(false);
        return v;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private static float clamp(float v) { return Math.max(0f, Math.min(100f, v)); }

    private class PetView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap[] frames = new Bitmap[8];
        private int frame = 0;
        private long nextFrame;
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        PetView(Context c) {
            super(c);
            int[] ids = {R.drawable.idle_01,R.drawable.idle_02,R.drawable.idle_03,R.drawable.idle_04,R.drawable.idle_05,R.drawable.idle_06,R.drawable.idle_07,R.drawable.idle_08};
            for (int i=0;i<8;i++) frames[i] = BitmapFactory.decodeResource(getResources(), ids[i]);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(), h=getHeight();
            LinearGradient lg = new LinearGradient(0,0,0,h,Color.rgb(27,30,58),Color.rgb(17,19,36),Shader.TileMode.CLAMP);
            paint.setShader(lg); c.drawRoundRect(new RectF(0,0,w,h), dp(24), dp(24), paint); paint.setShader(null);

            paint.setColor(Color.argb(45, 139,124,255));
            for (int i=0;i<12;i++) { float x=(i*97)%Math.max(1,w); float y=(i*53)%Math.max(1,h-80); c.drawCircle(x,y,2.2f,paint); }

            float floorY = h*0.75f; paint.setColor(Color.argb(60,255,255,255)); c.drawOval(new RectF(w*0.18f,floorY-8,w*0.82f,floorY+35), paint);
            Bitmap b=frames[frame]; if (b!=null) {
                float size=Math.min(w*0.78f, h*0.62f); float left=(w-size)/2f; float top=floorY-size*0.70f;
                src.set(0,0,b.getWidth(),b.getHeight()); dst.set(left,top,left+size,top+size); c.drawBitmap(b,src,dst,paint);
            }
            if (System.currentTimeMillis() >= nextFrame) { frame=(frame+1)%8; nextFrame=System.currentTimeMillis()+125; postInvalidateDelayed(125); }
            else postInvalidateDelayed(30);
        }
    }

    private static class PetState {
        float hunger=78, happiness=82, energy=75, clean=86, health=100;
        int coins=100, xp=0, level=1; long lastSaved=System.currentTimeMillis();
        private static final String P="pet_";
        static PetState load(Context c) {
            SharedPreferences s=c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); PetState x=new PetState();
            x.hunger=s.getFloat(P+"h",x.hunger); x.happiness=s.getFloat(P+"hap",x.happiness); x.energy=s.getFloat(P+"e",x.energy); x.clean=s.getFloat(P+"c",x.clean); x.health=s.getFloat(P+"hl",x.health); x.coins=s.getInt(P+"coins",x.coins); x.xp=s.getInt(P+"xp",x.xp); x.level=s.getInt(P+"lvl",x.level); x.lastSaved=s.getLong(P+"last",x.lastSaved); return x;
        }
        void save(Context c) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(P+"h",hunger).putFloat(P+"hap",happiness).putFloat(P+"e",energy).putFloat(P+"c",clean).putFloat(P+"hl",health).putInt(P+"coins",coins).putInt(P+"xp",xp).putInt(P+"lvl",level).putLong(P+"last",lastSaved).apply(); }
        int xpNeeded(){ return 100+(level-1)*35; }
        void addXp(int amount){ xp+=amount; while(xp>=xpNeeded()){ xp-=xpNeeded(); level++; coins+=15; } }
        void applyOfflineDecay(){ long now=System.currentTimeMillis(); long mins=Math.max(0,(now-lastSaved)/60000L); if(mins>0){ hunger=clamp(hunger-0.12f*mins); happiness=clamp(happiness-0.07f*mins); energy=clamp(energy-0.05f*mins); clean=clamp(clean-0.05f*mins); health=clamp(health-(hunger<15?0.03f:0.01f)*mins); lastSaved=now; }}
        void tick(){ applyOfflineDecay(); save(App.ctx); }
    }

    // Small static context holder used by the 30s game tick.
    static class App { static Context ctx; }

    @Override protected void onResume(){ super.onResume(); PetState.load(this); App.ctx=this; }
    @Override protected void onPause(){ super.onPause(); state.lastSaved=System.currentTimeMillis(); state.save(this); }
}
