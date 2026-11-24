package com.example.pengingat

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

class AlarmActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getLongExtra("id", 0)
        val d = resources.displayMetrics.density
        val notes = Store.load(this).find { it.id == id }?.notes ?: ""
        fun send(a: String) {
            startService(Intent(this, AlarmService::class.java).setAction(a).putExtra("id", id))
            finish()
        }
        fun txt(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun btn(t: String, bg: String, fg: String, a: String) = txt(t, 18f, true).apply {
            setTextColor(Color.parseColor(fg)); setPadding(0, (16 * d).toInt(), 0, (16 * d).toInt())
            background = GradientDrawable().apply { setColor(Color.parseColor(bg)); cornerRadius = 16 * d }
            setOnClickListener { send(a) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1B1F3A")); setPadding((28 * d).toInt(), 0, (28 * d).toInt(), 0)
        }
        root.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"; textSize = 64f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        root.addView(txt("🔔", 56f))
        root.addView(txt(intent.getStringExtra("title") ?: "", 28f, true).apply { setPadding(0, (8 * d).toInt(), 0, 0) })
        if (notes.isNotBlank()) root.addView(txt(notes, 16f).apply { alpha = .8f; setPadding(0, (8 * d).toInt(), 0, 0) })
        val gap = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (14 * d).toInt() }
        root.addView(btn("Selesai", "#818CF8", "#0F1220", "STOP"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = (40 * d).toInt() })
        root.addView(btn("Tunda ${Prefs.snooze(this)} menit", "#2A3050", "#FFFFFF", "SNOOZE"), gap)
        setContentView(root)
    }
}
