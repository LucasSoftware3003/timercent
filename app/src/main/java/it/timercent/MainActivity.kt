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

internal val BG = 0xFF12161C.toInt()
internal val CARD = 0xFF1E242D.toInt()
internal val FG = 0xFFEEF0F3.toInt()
internal val MUTE = 0xFF8A93A0.toInt()
internal val ACC = 0xFFFFB020.toInt()
internal val GO = 0xFF34D399.toInt()
internal val DARK = 0xFF111111.toInt()

class MainActivity : Activity() {
    lateinit var L: MutableList<T>
    lateinit var body: LinearLayout
    lateinit var b0: Button
    lateinit var b1: Button
    lateinit var b2: Button
    lateinit var bA: Button
    var expAl: String? = null
    var pickAl: String? = null
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
    var swLapTv: TextView? = null            // tempo del giro in corso (aggiornato dal ticker)
    val laps = ArrayList<Long>()             // tempo totale del cronometro a ogni pressione di «Giro»
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
        fun small(x: Button) = x.apply { textSize = 13f; minWidth = 0; maxLines = 1; setPadding(dp(2), 0, dp(2), 0); setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, TypedValue.COMPLEX_UNIT_SP) }
        bA = small(btn(getString(R.string.alarms_tab), BG, FG) { tab = 3; render() })
        b0 = small(btn(getString(R.string.tab_timer), CARD, FG) { tab = 0; render() })
        b1 = small(btn(getString(R.string.tab_stopwatch), BG, FG) { tab = 1; render() })
        b2 = small(btn(getString(R.string.tab_clock), BG, FG) { tab = 2; render() })
        val b3 = small(btn("⋮", BG, FG) { optDlg() })
        val top = LinearLayout(this).apply { setPadding(dp(8), dp(12), dp(8), 0) }
        top.addView(bA, lp(0, -2, 1f, 2)); top.addView(b0, lp(0, -2, 1f, 2)); top.addView(b1, lp(0, -2, 1.1f, 2)); top.addView(b2, lp(0, -2, 1f, 2)); top.addView(b3, lp(0, -2, 0.45f, 2))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(24)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        root.addView(top)
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        tabFor(intent)?.let { tab = it }
        if (b == null) handleIntent(intent, true)
    }
    // SET_ALARM e SET_TIMER con dati: creano sveglia o timer. Con SKIP_UI (assistente vocale) l'app non resta in primo piano.
    private fun handleIntent(i: android.content.Intent?, fresh: Boolean) {
        if (i == null) return
        val done = when (i.action) {
            "android.intent.action.SET_ALARM" -> alarmFromIntent(i)
            "android.intent.action.SET_TIMER" -> timerFromIntent(i)
            else -> false
        }
        if (done && i.getBooleanExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)) {
            if (fresh) finish() else moveTaskToBack(true)
        }
    }
    // Scheda da aprire: dal tocco sul widget o dalle richieste standard di Android ("mostra sveglie", "imposta timer"...)
    private fun tabFor(i: android.content.Intent?): Int? = when (i?.action) {
        "android.intent.action.SHOW_ALARMS", "android.intent.action.SET_ALARM" -> 3
        "android.intent.action.SHOW_TIMERS", "android.intent.action.SET_TIMER" -> 0
        else -> if (i?.hasExtra("tab") == true) i.getIntExtra("tab", 0).takeIf { it in 0..3 } else null
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent)
        tabFor(intent)?.let { tab = it }   // poi onResume ridisegna
        handleIntent(intent, false)
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
    // Scorrimento orizzontale tra le schede, nell'ordine dei pulsanti in alto: Sveglie, Timer, Cronometro, Orologio.
    // Non parte se il dito inizia su un cursore, un interruttore o un campo di testo (si muoverebbero con lo stesso gesto).
    private val tabOrder = intArrayOf(3, 0, 1, 2)
    private var swipeOk = false
    private var downX = 0f
    private var downY = 0f

    private fun noSwipeAt(v: View, x: Int, y: Int): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val r = android.graphics.Rect()
        if (!v.getGlobalVisibleRect(r) || !r.contains(x, y)) return false
        if (v is SeekBar || v is Switch || v is EditText || v is HorizontalScrollView) return true
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) if (noSwipeAt(v.getChildAt(i), x, y)) return true
        return false
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                swipeOk = !noSwipeAt(window.decorView, ev.rawX.toInt(), ev.rawY.toInt())
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN, android.view.MotionEvent.ACTION_CANCEL -> swipeOk = false
            android.view.MotionEvent.ACTION_UP -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                val next = tabOrder.indexOf(tab) + (if (dx < 0) 1 else -1)
                val fast = ev.eventTime - ev.downTime < 700
                if (swipeOk && fast && Math.abs(dx) > dp(90) && Math.abs(dx) > 2 * Math.abs(dy) && next in tabOrder.indices) {
                    swipeOk = false
                    // Il gesto non deve premere il pulsante sotto il dito: ai contenuti arriva un annullamento
                    val c = android.view.MotionEvent.obtain(ev)
                    c.action = android.view.MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(c); c.recycle()
                    tab = tabOrder[next]; render()
                    return true
                }
                swipeOk = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun optDlg() {
        val names = resources.getStringArray(R.array.precision_names)
        AlertDialog.Builder(this).setTitle(getString(R.string.options_title))
            .setItems(arrayOf(getString(R.string.menu_timer_sound), getString(R.string.menu_precision_fmt, names[prec - 1]), getString(R.string.alarm_settings_title), getString(R.string.menu_dials), getString(R.string.menu_backup_fmt, if (getSharedPreferences("ct", 0).getBoolean("backup", false)) getString(R.string.backup_on) else getString(R.string.backup_off)), getString(R.string.menu_about))) { _, w -> if (w == 0) pickSound() else if (w == 1) precDlg() else if (w == 2) alarmSettings() else if (w == 3) dialMenu() else if (w == 4) backupDlg() else aboutDlg() }
            .show()
    }
    fun precDlg() {
        AlertDialog.Builder(this).setTitle(getString(R.string.precision_title))
            .setSingleChoiceItems(resources.getStringArray(R.array.precision_options), prec - 1) { d, w ->
                prec = w + 1; getSharedPreferences("ct", 0).edit().putInt("prec", prec).apply(); d.dismiss(); render()
            }.setNegativeButton(getString(R.string.close), null).show()
    }

    fun backupDlg() {
        val p = getSharedPreferences("ct", 0)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(10), dp(22), 0) }
        c.addView(tvw(getString(R.string.backup_hint), 14f, MUTE))
        val g = android.widget.RadioGroup(this)
        g.addView(android.widget.RadioButton(this).apply { text = getString(R.string.backup_off); id = 1; setTextColor(FG) })
        g.addView(android.widget.RadioButton(this).apply { text = getString(R.string.backup_on); id = 2; setTextColor(FG) })
        g.check(if (p.getBoolean("backup", false)) 2 else 1)
        g.setOnCheckedChangeListener { _, id -> p.edit().putBoolean("backup", id == 2).apply() }
        c.addView(g)
        AlertDialog.Builder(this).setTitle(getString(R.string.backup_title)).setView(c)
            .setNegativeButton(getString(R.string.close), null).show()
    }

    fun render() {
        body.removeAllViews(); tv.clear(); fin.clear(); swTv = null; swLapTv = null; wRows.clear()
        (b0.background as GradientDrawable).setColor(if (tab == 0) CARD else BG)
        (b1.background as GradientDrawable).setColor(if (tab == 1) CARD else BG)
        (b2.background as GradientDrawable).setColor(if (tab == 2) CARD else BG)
        (bA.background as GradientDrawable).setColor(if (tab == 3) CARD else BG)
        if (tab == 0) {
            L.forEach { card(it) }
            if (L.isEmpty()) body.addView(tvw(getString(R.string.no_timers), 15f, MUTE).apply { setPadding(dp(8), dp(24), dp(8), dp(24)) })
            body.addView(btn(getString(R.string.new_timer), CARD, FG) { addDlg() })
        } else if (tab == 1) stopwatch() else if (tab == 3) alarmsTab() else world()
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
        top.addView(tvw(t.label.ifEmpty { getString(R.string.default_timer_label) }, 18f, FG).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1 }, lp(0, -2, 1f))
        top.addView(tvw("✕", 20f, MUTE).apply {
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { Notif.stop(this@MainActivity, t); L.remove(t); save(); render() }
        })
        val time = tvw(fmt(rem(t)), 46f, if (done) ACC else FG); light(time); fit(time, 46)
        tv[t.id] = time
        val row = LinearLayout(this)
        if (!done) row.addView(btn(getString(R.string.reset), BG, FG) {
            Notif.stop(this, t); t.end = 0; t.left = t.ms; save(); render()
        }, lp(0, -2, 1f, 4))
        val label = if (done) getString(R.string.action_stop) else if (run) getString(R.string.pause) else if (t.left < t.ms) getString(R.string.resume) else getString(R.string.start)
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
        if (rq == 4 && rs == RESULT_OK && d != null) {
            val u = d.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            groupSoundPicked(u?.toString() ?: "")
        }
        if (rq == 3 && rs == RESULT_OK && d != null) {
            val u = d.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            pickAl?.let { id -> Alarms.find(this, id)?.let { it.snd = u?.toString() ?: ""; Alarms.put(this, it) } }
            render()
        }
    }

    fun addDlg() {
        val lb = EditText(this).apply { hint = getString(R.string.label_optional); textSize = 18f }
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
        AlertDialog.Builder(this).setTitle(getString(R.string.new_timer)).setView(c)
            .setPositiveButton(getString(R.string.add_btn)) { _, _ ->
                val p = d.padStart(6, '0')
                val ms = (p.substring(0, 2).toLong() * 3600 + p.substring(2, 4).toLong() * 60 + p.substring(4).toLong()) * 1000
                if (ms > 0) { L.add(T(System.currentTimeMillis().toString(), lb.text.toString().trim(), ms, ms, 0)); save(); render() }
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    fun stopwatch() {
        val d = tvw(fmt(swNow()), 56f, FG).apply { gravity = Gravity.CENTER }; light(d); fit(d, 56)
        swTv = d
        body.addView(d, lp(-1, -2).apply { topMargin = dp(40); bottomMargin = dp(32) })
        val r = LinearLayout(this)
        // In corsa il pulsante di sinistra segna un giro; in pausa azzera cronometro e giri
        if (swRun) r.addView(btn(getString(R.string.lap), CARD, FG) { laps.add(swNow()); render() }, lp(0, -2, 1f, 4))
        else r.addView(btn(getString(R.string.reset), CARD, FG) { swAcc = 0; swRun = false; laps.clear(); render() }, lp(0, -2, 1f, 4))
        r.addView(btn(if (swRun) getString(R.string.pause) else getString(R.string.start), if (swRun) ACC else GO, DARK) {
            if (swRun) { swAcc = swNow(); swRun = false } else { swT0 = SystemClock.elapsedRealtime(); swRun = true }
            render()
        }, lp(0, -2, 1f, 4))
        body.addView(r)
        if (laps.isNotEmpty()) lapList()
    }

    // Elenco dei giri, dal più recente: tempo del giro e tempo totale. Il migliore è verde, il peggiore rosso (da 2 giri in su).
    private fun lapList() {
        val n = laps.size
        val times = LongArray(n) { laps[it] - (if (it == 0) 0L else laps[it - 1]) }
        val best = if (n >= 2) times.withIndex().minByOrNull { it.value }?.index else null
        val worst = if (n >= 2) times.withIndex().maxByOrNull { it.value }?.index else null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(20).toFloat() }
        }
        fun row(label: String, lap: String, total: String, col: Int): TextView {
            val line = LinearLayout(this).apply { setPadding(0, dp(8), 0, dp(8)) }
            val lapTv = tvw(lap, 18f, col).apply { gravity = Gravity.END; fontFeatureSettings = "tnum" }
            line.addView(tvw(label, 15f, MUTE), lp(0, -2, 0.8f))
            line.addView(lapTv, lp(0, -2, 1.4f))
            line.addView(tvw(total, 13f, MUTE).apply { gravity = Gravity.END; fontFeatureSettings = "tnum" }, lp(0, -2, 1.4f))
            box.addView(line)
            return lapTv
        }
        // Giro in corso (solo mentre il cronometro corre)
        if (swRun) swLapTv = row(getString(R.string.lap_n, n + 1), fmt(swNow() - laps[n - 1]), "", FG)
        for (i in n - 1 downTo 0) {
            val col = if (i == best) GO else if (i == worst) 0xFFF87171.toInt() else FG
            row(getString(R.string.lap_n, i + 1), fmt(times[i]), fmt(laps[i]), col)
        }
        body.addView(box, lp(-1, -2).apply { topMargin = dp(20) })
    }

    fun upd() {
        if (tab == 0) {
            val now = System.currentTimeMillis()
            for (t in L) {
                tv[t.id]?.text = fmt(rem(t))
                if (t.end in 1..now && t.id !in fin) { Notif.fire(this, t); render(); return }
            }
        } else if (tab == 1) {
            swTv?.text = fmt(swNow())
            swLapTv?.text = fmt(swNow() - (laps.lastOrNull() ?: 0L))
        }
        else if (tab == 2) { val sec = System.currentTimeMillis() / 1000; if (sec != wLast) { wLast = sec; updWorld() } }
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
        val day = when (d) { 0L -> getString(R.string.today); 1L -> getString(R.string.tomorrow); -1L -> getString(R.string.yesterday); else -> "" }
        val diff = (z.rules.getOffset(now).totalSeconds - here.rules.getOffset(now).totalSeconds) / 60
        val off = if (diff == 0) getString(R.string.same_zone) else {
            val a = Math.abs(diff); val hh = a / 60; val mm = a % 60
            (if (diff > 0) "+" else "-") + (if (hh > 0) getString(R.string.hours_short, hh) else "") + (if (hh > 0 && mm > 0) " " else "") + (if (mm > 0) getString(R.string.minutes_short, mm) else "")
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
        if (l.isEmpty()) body.addView(tvw(getString(R.string.add_city_hint), 15f, MUTE).apply { setPadding(dp(8), dp(12), dp(8), dp(24)) })
        body.addView(btn(getString(R.string.add_city), CARD, FG) { cityDlg() })
        updWorld()
    }

    fun worldCard(id: String, local: Boolean, idx: Int, l: MutableList<String>) {
        val z = try { java.time.ZoneId.of(id) } catch (e: Exception) { return }
        val c = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(20).toFloat() }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(tvw(cityName(id) + if (local) getString(R.string.here_suffix) else "", 18f, FG).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
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
        if (i > 0) o.add(getString(R.string.move_up))
        if (i < l.size - 1) o.add(getString(R.string.move_down))
        if (o.isEmpty()) return
        AlertDialog.Builder(this).setTitle(cityName(l[i])).setItems(o.toTypedArray()) { _, w ->
            val j = if (o[w] == getString(R.string.move_up)) i - 1 else i + 1
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
        val et = EditText(this).apply { hint = getString(R.string.search_city); setSingleLine() }
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
        dlg = AlertDialog.Builder(this).setTitle(getString(R.string.add_city)).setView(c).setNegativeButton(getString(R.string.close), null).show()
    }
}
