package com.alfread.autotrigger.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class EdgePreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f; color = 0xFFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
    var sideRight = true
    var position = .5f
    override fun onDraw(c: Canvas) {
        val x = if (sideRight) width - 8f else 8f
        c.drawLine(x, height * position - 40, x, height * position + 40, p)
    }
}
