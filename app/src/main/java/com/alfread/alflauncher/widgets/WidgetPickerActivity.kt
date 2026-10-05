package com.alfread.alflauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

class WidgetPickerActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private val hostId = 0xA1F
    private val pickRequest = 2001
    private val configureRequest = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, hostId)
        host.startListening()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "Add Android Widget"
            textSize = 25f
        })
        layout.addView(TextView(this).apply {
            text = "Tap here to open Android's real widget picker. The selected provider is an installed Android AppWidget."
            textSize = 16f
            setPadding(0, 28, 0, 28)
        })
        layout.setOnClickListener { openPicker() }
        setContentView(layout)
    }

    private fun openPicker() {
        val id = host.allocateAppWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        startActivityForResult(intent, pickRequest)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickRequest) {
            if (resultCode != RESULT_OK || data == null) {
                finish()
                return
            }
            val id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id == -1) {
                finish()
                return
            }
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
            if (info?.configure != null) {
                startActivityForResult(
                    Intent(this, info.configure).putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, id
                    ),
                    configureRequest
                )
            } else {
                finishWithWidget(id)
            }
        } else if (requestCode == configureRequest) {
            if (resultCode == RESULT_OK && data != null) {
                finishWithWidget(
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                )
            } else {
                finish()
            }
        }
    }

    private fun finishWithWidget(id: Int) {
        if (id > 0) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }
        finish()
    }

    override fun onDestroy() {
        runCatching { host.stopListening() }
        super.onDestroy()
    }
}
