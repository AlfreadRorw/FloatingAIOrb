package com.metagz.supersuscheat;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Switch;

import java.util.HashMap;
import java.util.Map;

public class CheatConfig {

    public static final String PREFS = "metagz_prefs";

    // Toggle IDs -> view IDs (accessed via reflection-free lookup)
    public static final String[] TOGGLE_KEYS = {
        // Combat
        "god_mode", "unlimited_ammo", "no_recoil", "damage_x10", "auto_headshot",
        "rapid_fire", "freeze_enemies", "auto_heal", "infinite_grenade", "one_hit_kill",
        // Movement
        "speed_hack", "jump_boost", "fly_mode", "no_clip", "teleport", "moonwalk",
        // Visual
        "esp_players", "esp_items", "wallhack", "radar_hack", "remove_fog", "chams",
        // Economy
        "unlimited_gold", "unlimited_gems", "unlock_all", "xp_boost", "no_cooldown"
    };

    // Default states
    public static final Map<String, Boolean> defaults = new HashMap<>();
    static {
        for (String k : TOGGLE_KEYS) defaults.put(k, false);
    }

    public static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context ctx, String key) {
        return getPrefs(ctx).getBoolean(key, false);
    }

    public static void setOn(Context ctx, String key, boolean val) {
        getPrefs(ctx).edit().putBoolean(key, val).apply();
    }

    public static String dumpAll(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("# METAGZ Cheat Config v2\n");
        sb.append("# generated at ").append(System.currentTimeMillis()).append("\n");
        for (String k : TOGGLE_KEYS) {
            sb.append(k).append("=").append(isOn(ctx, k) ? 1 : 0).append("\n");
        }
        return sb.toString();
    }

    public static void saveProfile(Context ctx, String name) {
        SharedPreferences p = getPrefs(ctx);
        SharedPreferences.Editor e = p.edit();
        for (String k : TOGGLE_KEYS) {
            e.putBoolean("profile_" + name + "_" + k, p.getBoolean(k, false));
        }
        e.apply();
    }

    public static void loadProfile(Context ctx, String name) {
        SharedPreferences p = getPrefs(ctx);
        SharedPreferences.Editor e = p.edit();
        for (String k : TOGGLE_KEYS) {
            e.putBoolean(k, p.getBoolean("profile_" + name + "_" + k, false));
        }
        e.apply();
    }

    public static void resetAll(Context ctx) {
        SharedPreferences.Editor e = getPrefs(ctx).edit();
        for (String k : TOGGLE_KEYS) e.putBoolean(k, false);
        e.apply();
    }

    // Sync UI switches with prefs
    public static void refreshUI(Activity a) {
        int[] ids = {
            R.id.swGodMode, R.id.swUnlimitedAmmo, R.id.swNoRecoil, R.id.swDamageX10, R.id.swAutoHeadshot,
            R.id.swRapidFire, R.id.swFreezeEnemies, R.id.swAutoHeal, R.id.swInfiniteGrenade, R.id.swOneHitKill,
            R.id.swSpeedHack, R.id.swJumpBoost, R.id.swFlyMode, R.id.swNoClip, R.id.swTeleport, R.id.swMoonwalk,
            R.id.swEspPlayers, R.id.swEspItems, R.id.swWallhack, R.id.swRadarHack, R.id.swRemoveFog, R.id.swChams,
            R.id.swUnlimitedGold, R.id.swUnlimitedGems, R.id.swUnlockAll, R.id.swXpBoost, R.id.swNoCooldown
        };
        for (int i = 0; i < ids.length && i < TOGGLE_KEYS.length; i++) {
            Switch sw = a.findViewById(ids[i]);
            if (sw != null) {
                String key = TOGGLE_KEYS[i];
                sw.setChecked(isOn(a, key));
                sw.setOnCheckedChangeListener((v, checked) -> setOn(a, key, checked));
            }
        }
    }
}
