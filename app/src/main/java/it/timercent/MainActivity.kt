package it.timercent

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

private val BG = 0xFF12161C.toInt()
private val CARD = 0xFF1E242D.toInt()
private val FG = 0xFFEEF0F3.toInt()
private val MUTE = 0xFF8A93A0.toInt()
private val ACC = 0xFFFFB020.toInt()
private val GO = 0xFF34D399.toInt()
private val DARK = 0xFF111111.toInt()

class MainActivity : Activity() {
    lateinit var L: MutableList<T>
    lateinit var body: LinearLayout
    lateinit var b0: Button
    lateinit var b1: Button
    lateinit var b2: Button
    val h = Handler(Looper.getMainLooper())
    val tv = HashMap<String, TextView>()
    val fin = HashSet<String>()
    var tab = 0
    var prec = 2
    private val refresh = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { L = Store.load(this@MainActivity); render() }
    }
    var swAcc = 0L
    var swT0 = 0L
    var swRun = false
    var swTv: TextView? = null
    val ticker = object : Runnable { override fun run() { upd(); h.postDelayed(this, 30) } }

    fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    fun lp(w: Int, hh: Int, wt: Float = 0f, m: Int = 0) =
        LinearLayout.LayoutParams(w, hh, wt).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }
    fun tvw(s: String, sz: Float, c: Int) = TextView(this).apply { text = s; textSize = sz; setTextColor(c) }
    fun btn(s: String, bg: Int, fg: Int, f: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 16f; setTextColor(fg); minHeight = dp(56)
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(16).toFloat() }
        setOnClickListener { f() }
    }
    fun fmt(ms: Long): String {
        val m = maxOf(0L, ms)
        val f = (m % 1000).toInt()
        val fs = when (prec) { 1 -> "%d".format(f / 100); 2 -> "%02d".format(f / 10); else -> "%03d".format(f) }
        val s = m / 1000
        return (if (s >= 3600) "${s / 3600}:" else "") + "%02d:%02d.%s".format(s / 60 % 60, s % 60, fs)
    }
    fun fit(v: TextView, max: Int) { v.maxLines = 1; v.setAutoSizeTextTypeUniformWithConfiguration(14, max, 1, TypedValue.COMPLEX_UNIT_SP) }
    fun rem(t: T) = if (t.end > 0) maxOf(0L, t.end - System.currentTimeMillis()) else t.left
    fun swNow() = swAcc + (if (swRun) SystemClock.elapsedRealtime() - swT0 else 0L)
    fun save() = Store.save(this, L)
    fun light(v: TextView) { v.fontFeatureSettings = "tnum"; v.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Notif.channels(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        window.statusBarColor = BG; window.navigationBarColor = BG
        L = Store.load(this)
        prec = getSharedPreferences("ct", 0).getInt("prec", 2).coerceIn(1, 3)
        fun small(x: Button) = x.apply { textSize = 13f; minWidth = 0; maxLines = 1; setPadding(dp(2), 0, dp(2), 0) }
        b0 = small(btn("Timer", CARD, FG) { tab = 0; render() })
        b1 = small(btn("Cronometro", BG, FG) { tab = 1; render() })
        b2 = small(btn("Orologio", BG, FG) { tab = 2; render() })
        val b3 = small(btn("⋮", BG, FG) { optDlg() })
        val top = LinearLayout(this).apply { setPadding(dp(12), dp(12), dp(12), 0) }
        top.addView(b0, lp(0, -2, 1f, 4)); top.addView(b1, lp(0, -2, 1f, 4)); top.addView(b2, lp(0, -2, 1f, 4)); top.addView(b3, lp(0, -2, 0.45f, 4))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(24)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        root.addView(top)
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (intent?.hasExtra("tab") == true) tab = intent.getIntExtra("tab", 0)
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.hasExtra("tab")) tab = intent.getIntExtra("tab", 0)   // poi onResume ridisegna
    }
    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        L = Store.load(this)
        val flt = IntentFilter("it.timercent.REFRESH")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refresh, flt, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(refresh, flt)
        render(); h.post(ticker)
    }
    override fun onPause() {
        super.onPause(); h.removeCallbacks(ticker)
        try { unregisterReceiver(refresh) } catch (e: Exception) { }
    }
    fun optDlg() {
        val names = listOf("decimi", "centesimi", "millesimi")
        AlertDialog.Builder(this).setTitle("Opzioni")
            .setItems(arrayOf("Suono allarme", "Precisione: " + names[prec - 1])) { _, w -> if (w == 0) pickSound() else precDlg() }
            .show()
    }
    fun precDlg() {
        AlertDialog.Builder(this).setTitle("Precisione")
            .setSingleChoiceItems(arrayOf("Decimi (0.1 s)", "Centesimi (0.01 s)", "Millesimi (0.001 s)"), prec - 1) { d, w ->
                prec = w + 1; getSharedPreferences("ct", 0).edit().putInt("prec", prec).apply(); d.dismiss(); render()
            }.setNegativeButton("Chiudi", null).show()
    }

    fun render() {
        body.removeAllViews(); tv.clear(); fin.clear(); swTv = null; wRows.clear()
        (b0.background as GradientDrawable).setColor(if (tab == 0) CARD else BG)
        (b1.background as GradientDrawable).setColor(if (tab == 1) CARD else BG)
        (b2.background as GradientDrawable).setColor(if (tab == 2) CARD else BG)
        if (tab == 0) {
            L.forEach { card(it) }
            if (L.isEmpty()) body.addView(tvw("Nessun timer. Aggiungine uno con etichetta e durata.", 15f, MUTE).apply { setPadding(dp(8), dp(24), dp(8), dp(24)) })
            body.addView(btn("Nuovo timer", CARD, FG) { addDlg() })
        } else if (tab == 1) stopwatch() else world()
    }

    fun card(t: T) {
        val run = t.end > 0
        val done = run && rem(t) <= 0
        if (done) fin.add(t.id)
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(20).toFloat(); if (done) setStroke(dp(3), ACC) }
        }
        val top = LinearLayout(this)
        top.addView(tvw(t.label.ifEmpty { "Timer" }, 18f, FG).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1 }, lp(0, -2, 1f))
        top.addView(tvw("✕", 20f, MUTE).apply {
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { Notif.stop(this@MainActivity, t); L.remove(t); save(); render() }
        })
        val time = tvw(fmt(rem(t)), 46f, if (done) ACC else FG); light(time); fit(time, 46)
        tv[t.id] = time
        val row = LinearLayout(this)
        if (!done) row.addView(btn("Azzera", BG, FG) {
            Notif.stop(this, t); t.end = 0; t.left = t.ms; save(); render()
        }, lp(0, -2, 1f, 4))
        val label = if (done) "Ferma" else if (run) "Pausa" else if (t.left < t.ms) "Riprendi" else "Avvia"
        row.addView(btn(label, if (run) ACC else GO, DARK) {
            val d = t.end > 0 && rem(t) <= 0
            if (d) { Notif.stop(this, t); Notif.halt(this); t.end = 0; t.left = t.ms }
            else if (t.end > 0) { t.left = rem(t); t.end = 0; Notif.stop(this, t) }
            else { if (t.left <= 0) t.left = t.ms; t.end = System.currentTimeMillis() + t.left; Notif.start(this, t) }
            save(); render()
        }, lp(0, -2, 1f, 4))
        c.addView(top); c.addView(time); c.addView(row)
        body.addView(c, lp(-1, -2).apply { bottomMargin = dp(12) })
    }

    fun pickSound() {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Notif.sound(this))
        startActivityForResult(i, 2)
    }
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rq == 2 && rs == RESULT_OK && d != null) {
            val u = d.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            getSharedPreferences("ct", 0).edit().putString("snd", u?.toString() ?: "").apply()
        }
    }

    fun addDlg() {
        val lb = EditText(this).apply { hint = "Etichetta (facoltativa)"; textSize = 18f }
        var d = ""
        val disp = tvw("", 38f, FG).apply { gravity = Gravity.CENTER; fontFeatureSettings = "tnum" }
        fun refresh() { val p = d.padStart(6, '0'); disp.text = "${p.substring(0, 2)}h ${p.substring(2, 4)}m ${p.substring(4)}s" }
        refresh()
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 4) {
            val row = LinearLayout(this)
            for (k in keys.subList(r * 3, r * 3 + 3)) row.addView(btn(k, CARD, FG) {
                if (k == "⌫") d = d.dropLast(1)
                else { val n = (d + k).trimStart('0'); if (n.length <= 6) d = n }
                refresh()
            }.apply { textSize = 24f; minHeight = dp(68) }, lp(0, -2, 1f, 3))
            grid.addView(row)
        }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        c.addView(lb); c.addView(disp, lp(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(12) }); c.addView(grid)
        AlertDialog.Builder(this).setTitle("Nuovo timer").setView(c)
            .setPositiveButton("Aggiungi") { _, _ ->
                val p = d.padStart(6, '0')
                val ms = (p.substring(0, 2).toLong() * 3600 + p.substring(2, 4).toLong() * 60 + p.substring(4).toLong()) * 1000
                if (ms > 0) { L.add(T(System.currentTimeMillis().toString(), lb.text.toString().trim(), ms, ms, 0)); save(); render() }
            }.setNegativeButton("Annulla", null).show()
    }

    fun stopwatch() {
        val d = tvw(fmt(swNow()), 56f, FG).apply { gravity = Gravity.CENTER }; light(d); fit(d, 56)
        swTv = d
        body.addView(d, lp(-1, -2).apply { topMargin = dp(40); bottomMargin = dp(32) })
        val r = LinearLayout(this)
        r.addView(btn("Azzera", CARD, FG) { swAcc = 0; swRun = false; render() }, lp(0, -2, 1f, 4))
        r.addView(btn(if (swRun) "Pausa" else "Avvia", if (swRun) ACC else GO, DARK) {
            if (swRun) { swAcc = swNow(); swRun = false } else { swT0 = SystemClock.elapsedRealtime(); swRun = true }
            render()
        }, lp(0, -2, 1f, 4))
        body.addView(r)
    }

    fun upd() {
        if (tab == 0) {
            val now = System.currentTimeMillis()
            for (t in L) {
                tv[t.id]?.text = fmt(rem(t))
                if (t.end in 1..now && t.id !in fin) { Notif.fire(this, t); render(); return }
            }
        } else if (tab == 1) swTv?.text = fmt(swNow())
        else { val sec = System.currentTimeMillis() / 1000; if (sec != wLast) { wLast = sec; updWorld() } }
    }

    // ---------- Orologio internazionale ----------
    class WRow(val z: java.time.ZoneId, val time: TextView, val sub: TextView)
    val wRows = ArrayList<WRow>()
    var wLast = 0L

    fun zones(): MutableList<String> =
        (getSharedPreferences("ct", 0).getString("wc", "") ?: "").split("|").filter { it.isNotEmpty() }.toMutableList()
    fun saveZones(l: List<String>) { getSharedPreferences("ct", 0).edit().putString("wc", l.joinToString("|")).apply() }

    fun cityName(id: String): String {
        val n: String? = try { android.icu.text.TimeZoneNames.getInstance(java.util.Locale.getDefault()).getExemplarLocationName(id) } catch (e: Exception) { null }
        return if (!n.isNullOrBlank()) n else id.substringAfterLast('/').replace('_', ' ')
    }

    fun gmt(z: java.time.ZoneId, now: java.time.Instant): String {
        val m = z.rules.getOffset(now).totalSeconds / 60
        val a = Math.abs(m)
        return "GMT" + (if (m < 0) "-" else "+") + (a / 60) + (if (a % 60 != 0) ":%02d".format(a % 60) else "")
    }

    fun subText(z: java.time.ZoneId, now: java.time.Instant): String {
        val here = java.time.ZoneId.systemDefault()
        val d = java.time.temporal.ChronoUnit.DAYS.between(now.atZone(here).toLocalDate(), now.atZone(z).toLocalDate())
        val day = when (d) { 0L -> "Oggi"; 1L -> "Domani"; -1L -> "Ieri"; else -> "" }
        val diff = (z.rules.getOffset(now).totalSeconds - here.rules.getOffset(now).totalSeconds) / 60
        val off = if (diff == 0) "stesso fuso" else {
            val a = Math.abs(diff); val hh = a / 60; val mm = a % 60
            (if (diff > 0) "+" else "-") + (if (hh > 0) "$hh h" else "") + (if (hh > 0 && mm > 0) " " else "") + (if (mm > 0) "$mm min" else "")
        }
        return listOf(day, off).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun updWorld() {
        val now = java.time.Instant.now()
        val f = java.time.format.DateTimeFormatter.ofPattern(
            if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a", java.util.Locale.getDefault())
        for (r in wRows) {
            val t = f.format(now.atZone(r.z)); if (r.time.text.toString() != t) r.time.text = t
            val x = subText(r.z, now); if (r.sub.text.toString() != x) r.sub.text = x
        }
    }

    fun world() {
        worldCard(java.time.ZoneId.systemDefault().id, true, 0, mutableListOf())
        val l = zones()
        l.forEachIndexed { i, id -> worldCard(id, false, i, l) }
        if (l.isEmpty()) body.addView(tvw("Aggiungi le città di cui vuoi vedere l'ora.", 15f, MUTE).apply { setPadding(dp(8), dp(12), dp(8), dp(24)) })
        body.addView(btn("Aggiungi città", CARD, FG) { cityDlg() })
        updWorld()
    }

    fun worldCard(id: String, local: Boolean, idx: Int, l: MutableList<String>) {
        val z = try { java.time.ZoneId.of(id) } catch (e: Exception) { return }
        val c = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(20).toFloat() }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(tvw(cityName(id) + if (local) " (qui)" else "", 18f, FG).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
        val sub = tvw("", 13f, MUTE)
        left.addView(sub)
        val time = tvw("", 36f, FG); light(time)
        c.addView(left, lp(0, -2, 1f)); c.addView(time)
        if (local) time.setPadding(0, 0, dp(8), 0)
        else {
            c.addView(tvw("✕", 20f, MUTE).apply {
                setPadding(dp(16), dp(8), dp(8), dp(8))
                setOnClickListener { l.removeAt(idx); saveZones(l); render() }
            })
            c.setOnLongClickListener { moveDlg(idx, l); true }
        }
        wRows.add(WRow(z, time, sub))
        body.addView(c, lp(-1, -2).apply { bottomMargin = dp(12) })
    }

    fun moveDlg(i: Int, l: MutableList<String>) {
        val o = ArrayList<String>()
        if (i > 0) o.add("Sposta su")
        if (i < l.size - 1) o.add("Sposta giù")
        if (o.isEmpty()) return
        AlertDialog.Builder(this).setTitle(cityName(l[i])).setItems(o.toTypedArray()) { _, w ->
            val j = if (o[w] == "Sposta su") i - 1 else i + 1
            val x = l[i]; l[i] = l[j]; l[j] = x; saveZones(l); render()
        }.show()
    }

    fun cityDlg() {
        val pre = listOf("Africa/", "America/", "Antarctica/", "Arctic/", "Asia/", "Atlantic/", "Australia/", "Europe/", "Indian/", "Pacific/")
        val now = java.time.Instant.now()
        val all = java.time.ZoneId.getAvailableZoneIds().filter { id -> pre.any { id.startsWith(it) } }
            .map { id -> Triple(cityName(id), id, gmt(java.time.ZoneId.of(id), now)) }
            .distinctBy { it.first + it.third }.sortedBy { it.first.lowercase() }
        var shown = all
        val ad = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        fun filt(q: String) {
            val k = q.trim().lowercase()
            shown = if (k.isEmpty()) all else all.filter { it.first.lowercase().contains(k) || it.second.lowercase().replace('_', ' ').contains(k) }
            ad.clear(); ad.addAll(shown.map { "${it.first}  (${it.third})" }); ad.notifyDataSetChanged()
        }
        filt("")
        val et = EditText(this).apply { hint = "Cerca città"; setSingleLine() }
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { filt(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        val lv = ListView(this); lv.adapter = ad
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        c.addView(et); c.addView(lv, LinearLayout.LayoutParams(-1, dp(320)))
        var dlg: AlertDialog? = null
        lv.setOnItemClickListener { _, _, pos, _ ->
            val id = shown[pos].second
            val l = zones(); if (id !in l) { l.add(id); saveZones(l) }
            dlg?.dismiss(); render()
        }
        dlg = AlertDialog.Builder(this).setTitle("Aggiungi città").setView(c).setNegativeButton("Chiudi", null).show()
    }
}
