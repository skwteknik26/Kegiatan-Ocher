package com.example.pengingat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// repeat: 0 = sekali, 1 = setiap hari, 2 = hari kerja, 3 = setiap minggu
data class Item(var id: Long, var title: String, var notes: String, var time: Long, var repeat: Int, var done: Boolean, var lead: Int = 0)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("pengingat", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Item> {
        val a = JSONArray(sp(c).getString("items", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Item(o.getLong("id"), o.getString("title"), o.optString("notes", ""),
                o.getLong("time"), o.optInt("repeat", 0), o.getBoolean("done"), o.optInt("lead", 0))
        }
    }

    fun save(c: Context, list: List<Item>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("notes", it.notes)
                .put("time", it.time).put("repeat", it.repeat).put("done", it.done).put("lead", it.lead))
        }
        sp(c).edit().putString("items", a.toString()).apply()
        PengingatWidget.refresh(c)
    }

    // Waktu kemunculan berikutnya untuk kegiatan berulang
    fun next(it: Item, after: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply { timeInMillis = it.time }
        do {
            c.add(Calendar.DAY_OF_YEAR, if (it.repeat == 3) 7 else 1)
            if (it.repeat == 2) while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) c.add(Calendar.DAY_OF_YEAR, 1)
        } while (c.timeInMillis <= after)
        return c.timeInMillis
    }

    // slot 0 = alarm utama, 1 = alarm tunda (kegiatan berulang), 2 = pengingat awal
    private fun pending(c: Context, it: Item, slot: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra("id", it.id).putExtra("title", it.title)
            .putExtra("pre", slot == 2)
        return PendingIntent.getBroadcast(c, it.id.toInt() * 3 + slot, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun set(c: Context, it: Item, at: Long, slot: Int) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(c, it, slot))
    }

    // setAlarmClock: tepat waktu, tetap bunyi walau HP idle / Doze
    fun schedule(c: Context, it: Item, at: Long = it.time, slot: Int = 0) {
        set(c, it, at, slot)
        if (slot == 0) {
            val pre = it.time - it.lead * 60_000L
            if (it.lead > 0 && pre > System.currentTimeMillis()) set(c, it, pre, 2)
            else c.getSystemService(AlarmManager::class.java).cancel(pending(c, it, 2))
        }
    }

    fun cancel(c: Context, it: Item) {
        val am = c.getSystemService(AlarmManager::class.java)
        for (s in 0..2) am.cancel(pending(c, it, s))
    }

    fun rescheduleAll(c: Context) {
        val list = load(c); val now = System.currentTimeMillis()
        list.filter { !it.done }.forEach {
            if (it.time <= now && it.repeat != 0) it.time = next(it)
            if (it.time > now) schedule(c, it)
        }
        save(c, list)
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun sound(c: Context): String? = sp(c).getString("sound", null)
    fun setSound(c: Context, s: String?) = sp(c).edit().putString("sound", s).apply()
    fun snooze(c: Context) = sp(c).getInt("snooze", 5)
    fun setSnooze(c: Context, n: Int) = sp(c).edit().putInt("snooze", n).apply()
    fun vibrate(c: Context) = sp(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = sp(c).edit().putBoolean("vibrate", v).apply()
    fun asked(c: Context) = sp(c).getBoolean("fsAsked", false)
    fun setAsked(c: Context) = sp(c).edit().putBoolean("fsAsked", true).apply()
}
