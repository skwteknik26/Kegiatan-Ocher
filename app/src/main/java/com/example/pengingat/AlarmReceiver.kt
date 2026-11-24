package com.example.pengingat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        PengingatWidget.refresh(c)
        val id = i.getLongExtra("id", 0)
        val title = i.getStringExtra("title") ?: ""
        if (i.getBooleanExtra("pre", false)) { pre(c, id, title); return }
        ContextCompat.startForegroundService(
            c, Intent(c, AlarmService::class.java).putExtra("id", id).putExtra("title", title)
        )
    }

    // Pengingat awal: notifikasi biasa (bukan alarm berbunyi terus)
    private fun pre(c: Context, id: Long, title: String) {
        val item = Store.load(c).find { it.id == id }
        if (item == null || item.done) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pre", "Pengingat awal", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val t = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(item.time))
        nm.notify(id.toInt(), Notification.Builder(c, "pre")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏳ $title").setContentText("Dimulai pukul $t · ${item.lead} menit lagi")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
