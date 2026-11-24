package com.example.pengingat

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.ServiceCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val id = i?.getLongExtra("id", 0) ?: 0
        when (i?.action) {
            "STOP" -> finish(id, false)
            "SNOOZE" -> finish(id, true)
            else -> ring(id, i?.getStringExtra("title") ?: "")
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Long, title: String) {
        val list = Store.load(this)
        val item = list.find { it.id == id }
        // Kegiatan berulang: langsung jadwalkan kemunculan berikutnya
        if (item != null && item.repeat != 0 && item.time <= System.currentTimeMillis() + 1000) {
            item.time = Store.next(item); Store.schedule(this, item); Store.save(this, list)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("alarm", "Alarm pengingat", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
        )
        val open = PendingIntent.getActivity(
            this, id.toInt(),
            Intent(this, AlarmActivity::class.java).putExtra("id", id).putExtra("title", title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun act(a: String, label: String) = Notification.Action.Builder(
            null, label,
            PendingIntent.getService(
                this, (id.toInt() * 3) + a.length,
                Intent(this, AlarmService::class.java).setAction(a).putExtra("id", id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()
        val notes = item?.notes?.takeIf { it.isNotBlank() } ?: "Waktunya kegiatan Anda"
        val n = Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $title").setContentText(notes)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(open, true).setContentIntent(open)
            .addAction(act("SNOOZE", "Tunda ${Prefs.snooze(this)} mnt")).addAction(act("STOP", "Selesai"))
            .setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        stopRing()
        try {
            val uri = Prefs.sound(this)?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare(); start()
            }
        } catch (e: Exception) { }
        if (Prefs.vibrate(this))
            getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        handler.postDelayed({ stopRing(); stopSelfClean() }, 120_000) // berhenti otomatis 2 menit
    }

    private fun finish(id: Long, snooze: Boolean) {
        val list = Store.load(this)
        list.find { it.id == id }?.let { item ->
            if (snooze) {
                val at = System.currentTimeMillis() + Prefs.snooze(this) * 60_000L
                if (item.repeat == 0) { item.time = at; item.done = false; Store.schedule(this, item) }
                else Store.schedule(this, item, at, 1)
            } else if (item.repeat == 0) item.done = true
            Store.save(this, list)
        }
        stopRing(); stopSelfClean()
    }

    private fun stopRing() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }; player = null
        getSystemService(Vibrator::class.java).cancel()
    }

    private fun stopSelfClean() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }

    override fun onDestroy() { stopRing(); super.onDestroy() }
}
