package com.example.pengingat

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Widget layar utama: 3 kegiatan berikutnya
class PengingatWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, build(c)) }
    }

    companion object {
        private fun build(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget)
            val f = SimpleDateFormat("EEE HH:mm", Locale("id", "ID"))
            val now = System.currentTimeMillis()
            val up = Store.load(c).filter { !it.done && it.time >= now }.sortedBy { it.time }.take(3)
            val lines = intArrayOf(R.id.l1, R.id.l2, R.id.l3)
            lines.forEachIndexed { n, id ->
                v.setTextViewText(id, up.getOrNull(n)?.let { f.format(Date(it.time)) + " · " + it.title }
                    ?: if (n == 0) "Tidak ada kegiatan mendatang" else "")
            }
            v.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            return v
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, PengingatWidget::class.java)).forEach { m.updateAppWidget(it, build(c)) }
        }
    }
}
