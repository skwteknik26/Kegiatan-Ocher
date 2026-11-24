package com.example.pengingat

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val loc = Locale("id", "ID")
    private val fDay = SimpleDateFormat("EEE, d MMM", loc)
    private val fTime = SimpleDateFormat("HH:mm", loc)
    private val repeatNames = listOf("Tidak berulang", "Setiap hari", "Hari kerja (Sen–Jum)", "Setiap minggu")
    private var cBg = 0; private var cCard = 0; private var cTx = 0; private var cMut = 0; private var cPri = 0
    private val cRed = Color.parseColor("#DC2626")
    private lateinit var list: LinearLayout
    private lateinit var quick: EditText
    private var settingsRefresh: (() -> Unit)? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun btn(t: String, bg: Int, fg: Int, f: () -> Unit) = tv(t, 15f, fg, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(14), dp(12), dp(14), dp(12)); background = round(bg, 12)
        setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        cBg = Color.parseColor(if (night) "#0F1220" else "#F4F5FA")
        cCard = Color.parseColor(if (night) "#1A1F36" else "#FFFFFF")
        cTx = Color.parseColor(if (night) "#EEF0F7" else "#1B1F2A")
        cMut = Color.parseColor(if (night) "#9AA1B5" else "#6B7280")
        cPri = Color.parseColor(if (night) "#818CF8" else "#4F46E5")
        window.statusBarColor = cBg; window.navigationBarColor = cBg
        if (!night) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cBg); setPadding(dp(16), dp(20), dp(16), 0) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("⏰ Pengingat", 24f, cTx, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(tv("⚙", 24f, cTx).apply { setPadding(dp(10), dp(4), dp(4), dp(4)); setOnClickListener { openSettings() } })
        root.addView(head)

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        quick = EditText(this).apply {
            hint = "Ketik kegiatan, mis. rapat besok jam 14:00"; setHintTextColor(cMut); setTextColor(cTx)
            textSize = 15f; setSingleLine(); background = round(cCard, 14); setPadding(dp(14), dp(12), dp(14), dp(12))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> quickAdd(); true }
        }
        row.addView(quick, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn("＋", cPri, if (night) Color.parseColor("#0F1220") else Color.WHITE) { quickAdd() },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        root.addView(row, lp(14))
        root.addView(tv("Contoh: minum obat 30 menit lagi · olahraga besok pagi · bayar listrik lusa jam 10", 12f, cMut), lp(6))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(32)) }
        root.addView(ScrollView(this).apply { addView(list); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 34 && !Prefs.asked(this) &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            Prefs.setAsked(this)
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
        render()
    }

    // ---------- Daftar ----------
    private fun render() {
        list.removeAllViews()
        val all = Store.load(this); val now = System.currentTimeMillis()
        val endToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
        val active = all.filter { !it.done }.sortedBy { it.time }
        section("TERLEWAT", active.filter { it.time < now }, cRed)
        section("HARI INI", active.filter { it.time in now..endToday }, cPri)
        section("MENDATANG", active.filter { it.time > endToday }, cPri)
        section("SELESAI", all.filter { it.done }.sortedByDescending { it.time }, cMut)
        if (all.isEmpty()) list.addView(tv("Belum ada pengingat.\nKetik di kolom atas untuk menambah.", 15f, cMut).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(48), 0, 0) })
        if (all.any { it.done }) list.addView(tv("Hapus semua yang selesai", 13f, cRed).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0)
            setOnClickListener { Store.save(this@MainActivity, Store.load(this@MainActivity).filter { !it.done }); render() }
        })
    }

    private fun section(name: String, items: List<Item>, color: Int) {
        if (items.isEmpty()) return
        list.addView(tv(name, 12f, color, true).apply { setPadding(dp(4), dp(16), 0, 0) })
        items.forEach { list.addView(card(it), lp(8)) }
    }

    private fun card(item: Item): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = round(cCard, 16); setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { openEditor(item) }
        }
        row.addView(tv(if (item.done) "●" else "○", 24f, cPri).apply {
            setPadding(0, 0, dp(14), 0); setOnClickListener { toggle(item) } })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(item.title, 16f, if (item.done) cMut else cTx, true).apply {
            if (item.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG })
        val d = Date(item.time)
        col.addView(tv(fDay.format(d) + " · " + fTime.format(d) + (if (item.repeat != 0) " · 🔁 " + repeatNames[item.repeat] else "") + (if (item.lead > 0) " · ⏳ ${item.lead} mnt" else ""), 13f, cMut))
        if (item.notes.isNotBlank()) col.addView(tv(item.notes, 13f, cMut))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun toggle(item: Item) {
        val all = Store.load(this); val t = all.find { it.id == item.id } ?: return
        if (t.repeat != 0 && !t.done) { t.time = Store.next(t); Store.cancel(this, t); Store.schedule(this, t) } // selesai untuk hari ini
        else if (t.done) { t.done = false; if (t.time > System.currentTimeMillis()) Store.schedule(this, t) }
        else { t.done = true; Store.cancel(this, t) }
        Store.save(this, all); render()
    }

    private fun persist(item: Item) {
        val all = Store.load(this); all.removeAll { it.id == item.id }; all.add(item)
        Store.save(this, all); Store.cancel(this, item); if (!item.done) Store.schedule(this, item); render()
    }

    // ---------- Input cepat ----------
    private fun quickAdd() {
        val p = parse(quick.text.toString())
        if (p.title.isBlank()) { toast("Tulis nama kegiatan dulu"); return }
        quick.text.clear()
        if (p.time == null) { openEditor(null, p.title); return }
        persist(Item(System.currentTimeMillis(), p.title, "", p.time, 0, false))
        toast("Alarm diset: " + fDay.format(Date(p.time)) + " " + fTime.format(Date(p.time)))
    }

    private class Parsed(val title: String, val time: Long?)

    private fun parse(input: String): Parsed {
        var s = input.trim(); val ic = RegexOption.IGNORE_CASE
        fun clean() = s.replace(Regex("\\b(pukul|pada)\\b", ic), "").replace(Regex("\\s+"), " ").trim()
        Regex("(\\d+)\\s*(menit|mnt|jam)\\s*lagi", ic).find(s)?.let {
            val n = it.groupValues[1].toLong()
            val ms = n * if (it.groupValues[2].lowercase() == "jam") 3_600_000L else 60_000L
            s = s.replace(it.value, ""); return Parsed(clean(), System.currentTimeMillis() + ms)
        }
        var day = -1; var h = -1; var mi = 0
        Regex("\\b(besok|lusa)\\b", ic).find(s)?.let { day = if (it.value.lowercase() == "besok") 1 else 2; s = s.replace(it.value, "") }
        Regex("(?:jam\\s*)?(\\d{1,2})[:.](\\d{2})", ic).find(s)?.let {
            h = it.groupValues[1].toInt(); mi = it.groupValues[2].toInt(); s = s.replace(it.value, "") }
        if (h < 0) Regex("jam\\s*(\\d{1,2})", ic).find(s)?.let { h = it.groupValues[1].toInt(); s = s.replace(it.value, "") }
        Regex("\\b(pagi|siang|sore|malam)\\b", ic).find(s)?.let {
            val w = it.value.lowercase()
            if (h < 0) { h = when (w) { "pagi" -> 8; "siang" -> 12; "sore" -> 16; else -> 20 }; mi = 0 }
            else if (w != "pagi" && h in 1..11 && !(w == "siang" && h >= 10)) h += 12
            s = s.replace(it.value, "")
        }
        if (h > 23 || mi > 59) return Parsed(input.trim(), null)
        if (h < 0 && day < 0) return Parsed(input.trim(), null)
        if (h < 0) h = 8
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (day > 0) c.add(Calendar.DAY_OF_YEAR, day) else if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return Parsed(clean(), c.timeInMillis)
    }

    // ---------- Editor lengkap ----------
    private fun openEditor(existing: Item?, preTitle: String = "") {
        val cal = Calendar.getInstance()
        if (existing != null) cal.timeInMillis = existing.time else { cal.add(Calendar.HOUR_OF_DAY, 1); cal.set(Calendar.MINUTE, 0) }
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        var rep = existing?.repeat ?: 0
        var lead = existing?.lead ?: 0
        val leads = listOf(0, 5, 10, 30, 60)
        fun et(h: String, t: String) = EditText(this).apply { hint = h; setText(t); setHintTextColor(cMut); setTextColor(cTx) }
        val titleEt = et("Nama kegiatan", existing?.title ?: preTitle)
        val notesEt = et("Catatan (opsional)", existing?.notes ?: "")
        val dayB = btn("", cBg, cTx) {}; val timeB = btn("", cBg, cTx) {}; val repB = btn("", cBg, cTx) {}; val leadB = btn("", cBg, cTx) {}
        fun refresh() {
            dayB.text = "📅 " + fDay.format(cal.time); timeB.text = "🕒 " + fTime.format(cal.time); repB.text = "🔁 " + repeatNames[rep]
            leadB.text = "⏳ Pengingat awal: " + if (lead == 0) "tidak ada" else "$lead menit sebelumnya"
        }
        dayB.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d -> cal.set(y, m, d); refresh() },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeB.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); refresh() },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        repB.setOnClickListener { rep = (rep + 1) % 4; refresh() }
        leadB.setOnClickListener { lead = leads[(leads.indexOf(lead) + 1) % leads.size]; refresh() }
        refresh()

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        box.addView(titleEt); box.addView(notesEt)
        val r2 = LinearLayout(this)
        r2.addView(dayB, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        r2.addView(timeB, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        box.addView(r2, lp(12)); box.addView(repB, lp(8)); box.addView(leadB, lp(8))

        val b = AlertDialog.Builder(this).setTitle(if (existing == null) "Pengingat baru" else "Ubah pengingat")
            .setView(box).setPositiveButton("Simpan", null).setNegativeButton("Batal", null)
        if (existing != null) b.setNeutralButton("Hapus") { _, _ ->
            Store.cancel(this, existing)
            Store.save(this, Store.load(this).filter { it.id != existing.id }); render()
        }
        val dlg = b.create(); dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleEt.text.toString().trim()
            if (title.isEmpty()) { toast("Isi nama kegiatan dulu"); return@setOnClickListener }
            val item = existing ?: Item(System.currentTimeMillis(), title, "", 0, 0, false)
            item.title = title; item.notes = notesEt.text.toString().trim(); item.repeat = rep; item.lead = lead; item.done = false
            item.time = cal.timeInMillis
            if (item.time <= System.currentTimeMillis()) {
                if (rep == 0) { toast("Waktu sudah lewat"); return@setOnClickListener }
                item.time = Store.next(item)
            }
            persist(item); dlg.dismiss()
            toast("Alarm diset: " + fDay.format(Date(item.time)) + " " + fTime.format(Date(item.time)))
        }
    }

    // ---------- Pengaturan ----------
    private fun soundName(): String = try {
        val u = Prefs.sound(this)?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        RingtoneManager.getRingtone(this, u).getTitle(this)
    } catch (e: Exception) { "Bawaan" }

    private fun openSettings() {
        val soundB = btn("", cBg, cTx) {}; val snB = btn("", cBg, cTx) {}; val vibB = btn("", cBg, cTx) {}
        fun ref() {
            soundB.text = "🔔 Nada: " + soundName(); snB.text = "💤 Tunda: ${Prefs.snooze(this)} menit"
            vibB.text = "📳 Getar: " + if (Prefs.vibrate(this)) "Aktif" else "Mati"
        }
        settingsRefresh = { ref() }
        soundB.setOnClickListener {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            Prefs.sound(this)?.let { i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
            startActivityForResult(i, 7)
        }
        snB.setOnClickListener { Prefs.setSnooze(this, when (Prefs.snooze(this)) { 5 -> 10; 10 -> 15; else -> 5 }); ref() }
        vibB.setOnClickListener { Prefs.setVibrate(this, !Prefs.vibrate(this)); ref() }
        ref()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        box.addView(soundB); box.addView(snB, lp(8)); box.addView(vibB, lp(8))
        AlertDialog.Builder(this).setTitle("Pengaturan alarm").setView(box).setPositiveButton("Tutup", null).show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 7 && res == RESULT_OK) {
            Prefs.setSound(this, data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString())
            settingsRefresh?.invoke()
        }
    }
}
