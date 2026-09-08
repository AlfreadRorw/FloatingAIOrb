package com.example.floatingaiorb

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class ConfirmSendActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val comment = intent.getStringExtra("comment").orEmpty()
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(42,42,42,42); background = GradientDrawable().apply{setColor(Color.rgb(20,25,40)); cornerRadius=32f} }
        box.addView(TextView(this).apply { text="Konfirmasi komentar"; textSize=22f; setTextColor(Color.WHITE) })
        box.addView(TextView(this).apply { text="AI sudah mengetik:\n\n$comment\n\nTekan Kirim untuk tindakan publik."; textSize=16f; setTextColor(0xFFD7DCEC.toInt()); setPadding(0,24,0,24) })
        val row=LinearLayout(this).apply{gravity=Gravity.END}
        val cancel=Button(this).apply{text="Batal"}; val send=Button(this).apply{text="Kirim"}
        row.addView(cancel); row.addView(send); box.addView(row)
        cancel.setOnClickListener { getSharedPreferences("orb", Context.MODE_PRIVATE).edit().remove("pending_tiktok_comment").apply(); finish() }
        send.setOnClickListener {
            // Service receives the explicit confirmation through a broadcast.
            sendBroadcast(Intent("com.example.floatingaiorb.CONFIRM_SEND").setPackage(packageName))
            finish()
        }
        setContentView(FrameLayout(this).apply { setPadding(28,28,28,28); addView(box, FrameLayout.LayoutParams(-1,-2,Gravity.CENTER)) })
    }
}
