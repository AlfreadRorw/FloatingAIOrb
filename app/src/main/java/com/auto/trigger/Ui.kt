package com.auto.trigger
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.rb(c: Int, r: Int, stroke: Boolean = false) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat(); if (stroke) setStroke(dp(1), T.line) }
fun Context.oval(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
fun hap(v: View) { if (S.haptic == 1) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
fun Context.label(t: String, sz: Float = 13f, bold: Boolean = false, c: Int = T.fg) = TextView(this).apply { text = t; textSize = sz; setTextColor(c); if (bold) setTypeface(typeface, Typeface.BOLD) }
fun Context.icon(res: Int, tint: Int = T.fg, sz: Int = 36, bg: Int = T.card, onClick: () -> Unit) = ImageView(this).apply {
    setImageResource(res); setColorFilter(tint); val p = dp(sz / 4); setPadding(p, p, p, p)
    layoutParams = LinearLayout.LayoutParams(dp(sz), dp(sz)).apply { setMargins(dp(2), 0, dp(2), 0) }
    background = rb(bg, sz / 3); setOnClickListener { hap(this); onClick() } }
fun Context.chip(t: String, sel: Boolean, onClick: () -> Unit) = TextView(this).apply {
    text = t; textSize = 12f; gravity = Gravity.CENTER; setTextColor(if (sel) T.bg else T.fg); setTypeface(typeface, Typeface.BOLD)
    background = rb(if (sel) T.fg else T.card, 12); setPadding(dp(14), dp(9), dp(14), dp(9))
    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
    setOnClickListener { hap(this); onClick() } }
fun Context.card(pad: Int = 14) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; background = rb(T.card, 20); setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) } }
fun Context.stepper(name: String, v: Int, mn: Int, mx: Int, st: Int, unit: String, set: (Int) -> Unit, changed: () -> Unit): View = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
    addView(label(name).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
    addView(icon(R.drawable.ic_minus, T.fg, 32, T.bg) { set((v - st).coerceAtLeast(mn)); changed() })
    addView(label(if (v == 0 && unit == "x") "∞" else "$v$unit", 12f, true).apply { gravity = Gravity.CENTER; minWidth = dp(56) })
    addView(icon(R.drawable.ic_add, T.fg, 32, T.bg) { set((v + st).coerceAtMost(mx)); changed() }) }
fun Context.toggle(name: String, on: Boolean, set: (Int) -> Unit, changed: () -> Unit): View = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
    addView(label(name).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
    addView(chip(if (on) "ON" else "OFF", on) { set(if (on) 0 else 1); changed() }) }
