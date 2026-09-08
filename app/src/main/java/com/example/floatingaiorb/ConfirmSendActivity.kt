package com.example.floatingaiorb

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class ConfirmSendActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("orb", MODE_PRIVATE)
        val title = intent.getStringExtra("title") ?: "Konfirmasi tindakan"
        val payload = intent.getStringExtra("comment").orEmpty()
        val message = intent.getStringExtra("message") ?: "Periksa dulu sebelum tindakan dijalankan."
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 42, 48, 42)
            setBackgroundColor(Color.rgb(10, 12, 18))
        }
        root.addView(TextView(this).apply { text = title; textSize = 22f; setTextColor(Color.WHITE); setPadding(0,0,0,18) })
        root.addView(TextView(this).apply { text = message; textSize = 15f; setTextColor(Color.LTGRAY); setPadding(0,0,0,18) })
        root.addView(TextView(this).apply { text = payload; textSize = 17f; setTextColor(Color.WHITE); setPadding(16,16,16,16) })
        val confirm = Button(this).apply { text = "Lanjutkan"; setOnClickListener {
            sendBroadcast(Intent("com.example.floatingaiorb.CONFIRM_SEND").setPackage(packageName))
            finish()
        } }
        val cancel = Button(this).apply { text = "Batal"; setOnClickListener { prefs.edit().remove("pending_action_type").remove("pending_action_payload").apply(); finish() } }
        root.addView(confirm, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        root.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}
