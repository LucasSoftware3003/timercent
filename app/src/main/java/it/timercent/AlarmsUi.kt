package it.timercent

import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.widget.*
import java.time.LocalDate

private val CARD2 = 0xFF2A323D.toInt()
private val DAYN = arrayOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
private val DAYL = arrayOf("L", "M", "M", "G", "V", "S", "D")

fun untilTxt(ms: Long): String {
    val m = maxOf(1L, (ms + 59999) / 60000)
    val d = m / 1440; val h = m / 60 % 24; val mi = m % 60
    val p = ArrayList<String>()
    if (d > 0) p.add(if (d == 1L) "1 giorno" else "$d giorni")
    if (h > 0) p.add(if (h == 1L) "1 ora" else "$h ore")
    if (mi > 0) p.add(if (mi == 1L) "1 minuto" else "$mi minuti")
    return if (p.size > 1) p.dropLast(1).joinToString(", ") + " e " + p.last() else p[0]
}

private fun minTxt(n: Int) = if (n == 1) "1 minuto" else "$n minuti"

// Quanto prima della sveglia compare la notifica "prossima sveglia" con il tasto Salta
private fun preTxt(n: Int) = when { n <= 0 -> "mai"; n < 60 -> minTxt(n); n == 60 -> "1 ora"; else -> "${n / 60} ore" }

private fun silTxt(n: Int) = if (n == 0) "Mai" else minTxt(n)

private fun gradTxt(n: Int) = if (n == 0) "No" else "$n secondi"

// Scelta singola per un'impostazione della sveglia (a differenza di choice(), che scrive nelle impostazioni generali)
private fun MainActivity.alChoice(title: String, vals: List<Int>, cur: Int, lab: (Int) -> String, set: (Int) -> Unit) {
    AlertDialog.Builder(this).setTitle(title)
        .setSingleChoiceItems(vals.map(lab).toTypedArray(), vals.indexOf(cur)) { d, w -> d.dismiss(); set(vals[w]) }
        .setNegativeButton("Annulla", null).show()
}

private fun durTxt(s: Int): String {
    val p = ArrayList<String>()
    if (s >= 3600) p.add("${s / 3600} h")
    if (s / 60 % 60 > 0) p.add("${s / 60 % 60} min")
    if (s % 60 > 0) p.add("${s % 60} s")
    return p.joinToString(" ")
}

private fun MainActivity.is24() = android.text.format.DateFormat.is24HourFormat(this)

private fun MainActivity.timeTxt(a: Al): String =
    if (is24()) "%02d:%02d".format(a.h, a.m)
    else "%d:%02d %s".format(if (a.h % 12 == 0) 12 else a.h % 12, a.m, if (a.h < 12) "AM" else "PM")

// Ordine dei giorni (0 = lunedì) a partire dall'inizio settimana scelto
private fun MainActivity.order(): List<Int> { val s = Alarms.weekStart(this) - 1; return List(7) { (s + it) % 7 } }

private fun MainActivity.daysTxt(a: Al): String {
    val d = a.days
    return when {
        d == 0 -> if (Alarms.next(a).toLocalDate() == LocalDate.now()) "Oggi" else "Domani"
        d == 127 -> "Ogni giorno"
        d == 31 -> "Lun-Ven"
        d == 96 -> "Sab, Dom"
        else -> order().filter { (d and (1 shl it)) != 0 }.joinToString(", ") { DAYN[it] }
    }
}

private fun MainActivity.toastNext(a: Al) {
    val ms = Alarms.nextEff(a).toInstant().toEpochMilli() - System.currentTimeMillis()
    Toast.makeText(this, "Sveglia tra " + untilTxt(ms), Toast.LENGTH_LONG).show()
}

// Modifica che riguarda l'orario o i giorni: riattiva la sveglia e la ripianifica
private fun MainActivity.edit(a: Al) {
    a.on = true; a.skip = 0L; Alarms.put(this, a); Alarms.schedule(this, a); toastNext(a); render()
}

private fun MainActivity.sndName(s: String?): String {
    if (s == "") return "Nessuno"
    val u = if (s == null) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(s)
    val t: String? = try { RingtoneManager.getRingtone(this, u)?.getTitle(this) } catch (e: Exception) { null }
    return if (s == null) "Predefinito" + (if (t != null) " ($t)" else "") else (t ?: "Personalizzato")
}

private fun MainActivity.mkSwitch(on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
    isChecked = on
    thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ACC, MUTE))
    trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0x66FFB020, 0x33FFFFFF))
    setOnCheckedChangeListener { _, v -> f(v) }
}

private fun MainActivity.row(title: String, value: String, f: () -> Unit): LinearLayout {
    val r = LinearLayout(this)
    r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(0, dp(12), 0, dp(12))
    r.addView(tvw(title, 16f, FG), lp(0, -2, 1f))
    r.addView(tvw(value, 14f, MUTE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(190) })
    r.setOnClickListener { f() }
    return r
}

private fun MainActivity.swRow(title: String, on: Boolean, f: (Boolean) -> Unit): LinearLayout {
    val r = LinearLayout(this)
    r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(0, dp(4), 0, dp(4))
    r.addView(tvw(title, 16f, FG), lp(0, -2, 1f))
    r.addView(mkSwitch(on, f))
    return r
}

fun MainActivity.alarmsTab() {
    alSwitcher()
    if (alGroups) { groupsTab(); return }
    val l = Alarms.load(this).sortedBy { it.h * 60 + it.m }
    if (l.isEmpty()) body.addView(tvw("Nessuna sveglia. Aggiungine una.", 15f, MUTE).apply { setPadding(dp(8), dp(24), dp(8), dp(24)) })
    l.forEach { alarmCard(it) }
    body.addView(btn("Nuova sveglia", CARD, FG) { newAlarm() })
}

private fun MainActivity.alarmCard(a: Al) {
    val open = expAl == a.id
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL; c.setPadding(dp(16), dp(12), dp(16), dp(12))
    c.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(20).toFloat() }

    val head = LinearLayout(this); head.gravity = Gravity.CENTER_VERTICAL
    val left = LinearLayout(this); left.orientation = LinearLayout.VERTICAL
    val time = tvw(timeTxt(a), 44f, if (a.on) FG else MUTE); light(time)
    if (open) time.setOnClickListener { pickTime(a) }
    left.addView(time)
    val skipped = a.on && a.skip > System.currentTimeMillis()
    val grName = Groups.of(this, a.id)?.name ?: ""
    left.addView(tvw(listOf(a.label, daysTxt(a), if (skipped) "prossima saltata" else "", if (a.always) "sempre attiva" else grName).filter { it.isNotEmpty() }.joinToString(" · "), 14f, if (a.on) ACC else MUTE))
    head.addView(left, lp(0, -2, 1f))
    head.addView(mkSwitch(a.on) { v ->
        a.on = v; if (v) a.skip = 0L
        Alarms.put(this, a)
        if (v) { Alarms.schedule(this, a); toastNext(a) } else Alarms.cancel(this, a)
        render()
    })
    c.addView(head)
    c.setOnClickListener { expAl = if (open) null else a.id; render() }

    if (open) {
        val days = LinearLayout(this)
        for (d in order()) {
            val sel = (a.days and (1 shl d)) != 0
            val dot = tvw(DAYL[d], 15f, if (sel) DARK else FG).apply {
                gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (sel) ACC else CARD2) }
                setOnClickListener { a.days = a.days xor (1 shl d); edit(a) }
            }
            val cell = FrameLayout(this)
            cell.addView(dot, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
            days.addView(cell, lp(0, dp(44), 1f))
        }
        c.addView(days, lp(-1, -2).apply { topMargin = dp(8) })
        c.addView(row("Etichetta", a.label.ifEmpty { "Nessuna" }) { labelDlg(a) })
        c.addView(row("Suono", sndName(a.snd)) { pickAl = a.id; pickAlSound(a) })
        c.addView(swRow("Vibrazione", a.vib) { v -> a.vib = v; Alarms.put(this, a) })
        c.addView(row("Silenzia dopo", silTxt(a.sil)) {
            alChoice("Silenzia dopo", listOf(1, 5, 10, 15, 20, 25, 30, 0), a.sil, { silTxt(it) }) { a.sil = it; Alarms.put(this, a); render() }
        })
        c.addView(row("Durata posticipo", minTxt(a.snz)) {
            alChoice("Durata posticipo", listOf(1, 5, 10, 15, 20, 25, 30), a.snz, { minTxt(it) }) { a.snz = it; Alarms.put(this, a); render() }
        })
        c.addView(row("Volume crescente", gradTxt(a.grad)) {
            alChoice("Volume crescente", listOf(0, 5, 10, 15, 20, 30, 60), a.grad, { gradTxt(it) }) { a.grad = it; Alarms.put(this, a); render() }
        })
        c.addView(row("Avviso prima della sveglia", preTxt(a.pre)) {
            alChoice("Avviso prima della sveglia", listOf(0, 15, 30, 60, 120, 180), a.pre, { preTxt(it) }) {
                a.pre = it; Alarms.put(this, a); if (a.on) Alarms.schedule(this, a); render()
            }
        })
        c.addView(swRow("Sempre attiva", a.always) { v -> setAlways(a, v) })
        if (a.days == 0) c.addView(swRow("Elimina dopo la suoneria", a.del) { v -> a.del = v; Alarms.put(this, a) })
        c.addView(tvw("Elimina sveglia", 15f, MUTE).apply {
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener { Alarms.cancel(this@alarmCard, a); Alarms.remove(this@alarmCard, a.id); expAl = null; render() }
        })
    }
    body.addView(c, lp(-1, -2).apply { bottomMargin = dp(12) })
}

// "Sempre attiva": i comandi dei gruppi non la toccano, quindi non può stare in un gruppo
private fun MainActivity.setAlways(a: Al, v: Boolean) {
    if (!v) { a.always = false; Alarms.put(this, a); return }
    val g = Groups.of(this, a.id)
    if (g == null) { a.always = true; Alarms.put(this, a); render(); return }
    AlertDialog.Builder(this).setTitle("Sempre attiva")
        .setMessage("Una sveglia sempre attiva non può stare in un gruppo. La sveglia verrà tolta da «" + g.name + "». Procedo?")
        .setPositiveButton("Procedo") { _, _ ->
            g.ids.remove(a.id); Groups.put(this, g)
            a.always = true; Alarms.put(this, a); render()
        }
        .setNegativeButton("Annulla") { _, _ -> render() }
        .setOnCancelListener { render() }.show()
}

private fun MainActivity.labelDlg(a: Al) {
    val et = EditText(this)
    et.setText(a.label); et.hint = "Etichetta"; et.setSingleLine(); et.setSelection(et.text.length)
    val box = FrameLayout(this); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(et)
    AlertDialog.Builder(this).setTitle("Etichetta").setView(box)
        .setPositiveButton("OK") { _, _ -> a.label = et.text.toString().trim(); Alarms.put(this, a); render() }
        .setNegativeButton("Annulla", null).show()
}

private fun MainActivity.pickTime(a: Al) {
    TimePickerDialog(this, { _, hh, mm -> a.h = hh; a.m = mm; edit(a) }, a.h, a.m, is24()).show()
}

fun MainActivity.newAlarm() {
    val n = java.time.LocalTime.now()
    TimePickerDialog(this, { _, hh, mm ->
        val a = Al(System.currentTimeMillis().toString(), hh, mm, 0, "", true, true, null, false)
        expAl = a.id; edit(a); fsiCheck()
    }, n.hour, n.minute, is24()).show()
}

// Su Android 14+ la schermata a tutto schermo può richiedere un permesso dedicato
private fun MainActivity.fsiCheck() {
    if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
        AlertDialog.Builder(this).setTitle("Permesso necessario")
            .setMessage("Per mostrare la sveglia a schermo acceso o bloccato serve il permesso «Notifiche a schermo intero».")
            .setPositiveButton("Apri impostazioni") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }.setNegativeButton("Non ora", null).show()
    }
}

private fun MainActivity.pickAlSound(a: Al) {
    val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val cur: Uri? = if (a.snd == null) def else if (a.snd == "") null else Uri.parse(a.snd)
    startActivityForResult(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, def)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur), 3)
}

// ---------- Impostazioni sveglie ----------
private fun MainActivity.choice(title: String, key: String, vals: List<Int>, cur: Int, lab: (Int) -> String) {
    AlertDialog.Builder(this).setTitle(title)
        .setSingleChoiceItems(vals.map(lab).toTypedArray(), vals.indexOf(cur)) { d, w ->
            Alarms.p(this).edit().putInt(key, vals[w]).apply()
            d.dismiss(); alarmSettings()
        }.setNegativeButton("Indietro") { _, _ -> alarmSettings() }.show()
}

// Qui restano solo le impostazioni che valgono per tutte le sveglie; le altre sono nella scheda di ogni sveglia
fun MainActivity.alarmSettings() {
    val p = Alarms.p(this)
    val bt = p.getInt("a_btn", 0); val wk = Alarms.weekStart(this)
    val wt = p.getInt("a_watch", 0)
    val btnN = { x: Int -> when (x) { 0 -> "Posticipa"; 1 -> "Ferma"; else -> "Nessuna azione" } }
    val wkn = { x: Int -> when (x) { 1 -> "Lunedì"; 6 -> "Sabato"; else -> "Domenica" } }
    val items = arrayOf(
        "Tasti del volume: " + btnN(bt),
        "Inizio settimana: " + wkn(wk),
        "Volume delle sveglie",
        "Controllo sveglia dallo smartwatch: " + (if (wt == 1) "attivi" else "disattivati"))
    AlertDialog.Builder(this).setTitle("Impostazioni sveglie").setItems(items) { _, w ->
        when (w) {
            0 -> choice("Tasti del volume", "a_btn", listOf(0, 1, 2), bt, btnN)
            1 -> choice("Inizio settimana", "a_week", listOf(1, 6, 7), wk, wkn)
            2 -> volDlg()
            else -> choice("Controllo sveglia dallo smartwatch (pausa = ferma, avanti = posticipa)", "a_watch", listOf(0, 1), wt) { if (it == 1) "Attivi" else "Disattivati" }
        }
    }.setNegativeButton("Chiudi", null).show()
}

private fun MainActivity.volDlg() {
    val am = getSystemService(AudioManager::class.java)
    val sb = SeekBar(this)
    sb.max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    sb.progress = am.getStreamVolume(AudioManager.STREAM_ALARM)
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
            if (u) try { am.setStreamVolume(AudioManager.STREAM_ALARM, p, 0) } catch (e: Exception) { }
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    val c = LinearLayout(this); c.orientation = LinearLayout.VERTICAL; c.setPadding(dp(20), dp(12), dp(20), 0)
    c.addView(tvw("Volume di sistema per sveglie e timer. A zero non suonano.", 14f, MUTE))
    c.addView(sb, lp(-1, -2).apply { topMargin = dp(16) })
    AlertDialog.Builder(this).setTitle("Volume delle sveglie").setView(c)
        .setPositiveButton("OK") { _, _ -> alarmSettings() }.show()
}

// ---------- Richieste standard di Android (assistente vocale, altre app) ----------
// SET_ALARM con ora: crea la sveglia (o riusa una identica già presente). Senza ora si apre soltanto la scheda.
fun MainActivity.alarmFromIntent(i: Intent): Boolean {
    if (!i.hasExtra(AlarmClock.EXTRA_HOUR)) return false
    val h = i.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
    val m = i.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
    if (h !in 0..23 || m !in 0..59) return false
    // Calendar: domenica = 1 ... sabato = 7; da noi bit 0 = lunedì ... bit 6 = domenica
    var days = 0
    i.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)?.forEach { if (it in 1..7) days = days or (1 shl ((it + 5) % 7)) }
    val label = i.getStringExtra(AlarmClock.EXTRA_MESSAGE)?.trim() ?: ""
    val vib = i.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true)
    val ring = i.getStringExtra(AlarmClock.EXTRA_RINGTONE)
    val snd: String? = if (ring == null) null else if (ring == AlarmClock.VALUE_RINGTONE_SILENT) "" else ring
    val a = Alarms.load(this).firstOrNull { it.h == h && it.m == m && it.days == days && it.label == label }
        ?: Al(System.currentTimeMillis().toString(), h, m, days, label, true, vib, snd, false)
    a.on = true; a.skip = 0L
    Alarms.put(this, a); Alarms.schedule(this, a)
    toastNext(a)
    expAl = a.id
    if (!i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) fsiCheck()
    return true
}

// SET_TIMER con durata: crea il timer e lo avvia subito
fun MainActivity.timerFromIntent(i: Intent): Boolean {
    if (!i.hasExtra(AlarmClock.EXTRA_LENGTH)) return false
    val s = i.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
    if (s !in 1..86400) return false
    val ms = s * 1000L
    val t = T(System.currentTimeMillis().toString(), i.getStringExtra(AlarmClock.EXTRA_MESSAGE)?.trim() ?: "", ms, ms, 0)
    t.end = System.currentTimeMillis() + ms
    val l = Store.load(this)
    l.add(t); Store.save(this, l)
    Notif.start(this, t)
    Toast.makeText(this, "Timer avviato: " + durTxt(s), Toast.LENGTH_LONG).show()
    return true
}

// ---------- Gruppi ----------
// La scheda Sveglie ha due pagine: l'elenco di tutte le sveglie e quella dei gruppi, che raccoglie i comandi comuni
private var alGroups = false
private var expGr: String? = null

private fun MainActivity.alSwitcher() {
    val r = LinearLayout(this)
    r.addView(btn("Sveglie", if (!alGroups) CARD else BG, FG) { alGroups = false; render() }, lp(0, -2, 1f, 2))
    r.addView(btn("Gruppi", if (alGroups) CARD else BG, FG) { alGroups = true; render() }, lp(0, -2, 1f, 2))
    body.addView(r, lp(-1, -2).apply { bottomMargin = dp(8) })
}

private fun MainActivity.groupsTab() {
    val gl = Groups.load(this)
    if (gl.isEmpty()) body.addView(tvw("Nessun gruppo. Un gruppo raccoglie più sveglie: puoi accenderle, spegnerle e saltarle insieme.", 15f, MUTE).apply { setPadding(dp(8), dp(24), dp(8), dp(24)) })
    gl.forEach { groupCard(it) }
    body.addView(btn("Nuovo gruppo", CARD, FG) { newGroup() })
}

// Accende o spegne una sveglia come fa il suo interruttore nell'elenco
private fun MainActivity.setAlarmOn(a: Al, v: Boolean) {
    a.on = v; if (v) a.skip = 0L
    Alarms.put(this, a)
    if (v) Alarms.schedule(this, a) else Alarms.cancel(this, a)
}

// Interruttore del gruppo: stesso stato a tutte le sveglie
private fun MainActivity.grSwitch(g: Gr, v: Boolean) {
    g.on = v; Groups.put(this, g)
    g.ids.forEach { id -> Alarms.find(this, id)?.let { setAlarmOn(it, v) } }
    render()
}

// "Salta la prossima giornata": come il tasto Salta della notifica, per ogni sveglia attiva del gruppo
private fun MainActivity.grSkip(g: Gr) {
    var n = 0
    g.ids.forEach { id ->
        val a = Alarms.find(this, id)
        if (a != null && a.on) {
            n++
            if (a.days == 0) {
                a.on = false; Alarms.put(this, a); Alarms.cancel(this, a)
                if (a.del) Alarms.remove(this, a.id)
            } else {
                a.skip = Alarms.nextEff(a).toInstant().toEpochMilli(); Alarms.put(this, a); Alarms.schedule(this, a)
            }
        }
    }
    Toast.makeText(this, if (n == 0) "Nessuna sveglia attiva nel gruppo" else "Saltata la prossima suoneria di $n sveglie", Toast.LENGTH_LONG).show()
    render()
}

private fun MainActivity.groupCard(g: Gr) {
    val open = expGr == g.id
    val all = Alarms.load(this)
    val als = g.ids.mapNotNull { id -> all.firstOrNull { it.id == id } }.sortedBy { it.h * 60 + it.m }
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL; c.setPadding(dp(16), dp(12), dp(16), dp(12))
    c.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(20).toFloat() }

    val head = LinearLayout(this); head.gravity = Gravity.CENTER_VERTICAL
    val left = LinearLayout(this); left.orientation = LinearLayout.VERTICAL
    left.addView(tvw(g.name.ifEmpty { "Gruppo" }, 22f, if (g.on) FG else MUTE).apply { typeface = Typeface.DEFAULT_BOLD })
    val n = als.size; val act = als.count { it.on }
    left.addView(tvw((if (n == 1) "1 sveglia" else "$n sveglie") + " · " + (if (act == 1) "1 attiva" else "$act attive"), 14f, if (g.on) ACC else MUTE))
    head.addView(left, lp(0, -2, 1f))
    head.addView(mkSwitch(g.on) { v -> grSwitch(g, v) })
    c.addView(head)
    c.setOnClickListener { expGr = if (open) null else g.id; render() }

    if (open) {
        if (als.isEmpty()) c.addView(tvw("Nessuna sveglia nel gruppo.", 14f, MUTE).apply { setPadding(0, dp(12), 0, dp(4)) })
        als.forEach { a ->
            val r = LinearLayout(this); r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(0, dp(6), 0, dp(6))
            val t = LinearLayout(this); t.orientation = LinearLayout.VERTICAL
            t.addView(tvw(timeTxt(a), 20f, if (a.on) FG else MUTE).apply { typeface = Typeface.DEFAULT_BOLD })
            t.addView(tvw(listOf(a.label, daysTxt(a)).filter { it.isNotEmpty() }.joinToString(" · "), 13f, MUTE))
            r.addView(t, lp(0, -2, 1f))
            r.addView(tvw("✕", 18f, MUTE).apply {
                setPadding(dp(10), 0, dp(10), 0)
                setOnClickListener { g.ids.remove(a.id); Groups.put(this@groupCard, g); render() }
            })
            r.addView(mkSwitch(a.on) { v -> setAlarmOn(a, v); if (v) toastNext(a); render() })
            c.addView(r)
        }
        c.addView(row("Salta la prossima giornata", "") { grSkip(g) })
        c.addView(row("Modifica gruppo", "") { grEdit(g) })
        c.addView(row("Aggiungi sveglie", "") { pickAlarms(g) })
        c.addView(row("Nome", g.name) { renameGroup(g) })
        c.addView(tvw("Elimina gruppo", 15f, MUTE).apply {
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener { deleteGroup(g) }
        })
    }
    body.addView(c, lp(-1, -2).apply { bottomMargin = dp(12) })
}

private fun MainActivity.nameBox(cur: String): Pair<FrameLayout, EditText> {
    val et = EditText(this)
    et.setText(cur); et.hint = "Nome del gruppo"; et.setSingleLine(); et.setSelection(et.text.length)
    val box = FrameLayout(this); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(et)
    return Pair(box, et)
}

private fun MainActivity.newGroup() {
    val (box, et) = nameBox("")
    AlertDialog.Builder(this).setTitle("Nuovo gruppo").setView(box)
        .setPositiveButton("Avanti") { _, _ ->
            val g = Gr(System.currentTimeMillis().toString(), et.text.toString().trim().ifEmpty { "Gruppo" }, true, ArrayList())
            Groups.put(this, g); expGr = g.id
            pickAlarms(g)
        }.setNegativeButton("Annulla", null).show()
}

private fun MainActivity.renameGroup(g: Gr) {
    val (box, et) = nameBox(g.name)
    AlertDialog.Builder(this).setTitle("Nome del gruppo").setView(box)
        .setPositiveButton("OK") { _, _ -> g.name = et.text.toString().trim().ifEmpty { "Gruppo" }; Groups.put(this, g); render() }
        .setNegativeButton("Annulla", null).show()
}

// Elenco delle sveglie che non sono ancora in nessun gruppo
private fun MainActivity.pickAlarms(g: Gr) {
    val used = Groups.load(this).flatMap { it.ids }.toSet()
    val free = Alarms.load(this).filter { it.id !in used && !it.always }.sortedBy { it.h * 60 + it.m }
    if (free.isEmpty()) {
        Toast.makeText(this, "Nessuna sveglia libera: sono tutte in un gruppo o sempre attive", Toast.LENGTH_LONG).show()
        render(); return
    }
    val names = free.map { timeTxt(it) + (if (it.label.isEmpty()) "" else " · " + it.label) + " · " + daysTxt(it) }.toTypedArray()
    val chk = BooleanArray(free.size)
    AlertDialog.Builder(this).setTitle("Scegli le sveglie")
        .setMultiChoiceItems(names, chk) { _, i, v -> chk[i] = v }
        .setPositiveButton("OK") { _, _ ->
            val add = free.filterIndexed { i, _ -> chk[i] }
            if (g.ids.isEmpty() && add.isNotEmpty()) g.on = add.any { it.on }
            add.forEach { g.ids.add(it.id) }
            Groups.put(this, g); render()
        }
        .setNegativeButton("Annulla") { _, _ -> render() }
        .setOnCancelListener { render() }.show()
}

private fun MainActivity.deleteGroup(g: Gr) {
    val n = g.ids.size
    val b = AlertDialog.Builder(this).setTitle("Elimina «" + g.name + "»")
    if (n == 0) {
        b.setMessage("Eliminare il gruppo?")
            .setPositiveButton("Elimina") { _, _ -> Groups.remove(this, g.id); expGr = null; render() }
    } else {
        b.setMessage(if (n == 1) "Il gruppo contiene 1 sveglia. Cosa vuoi fare?" else "Il gruppo contiene $n sveglie. Cosa vuoi fare?")
            .setPositiveButton("Solo il gruppo") { _, _ -> Groups.remove(this, g.id); expGr = null; render() }
            .setNeutralButton("Anche le sveglie") { _, _ ->
                g.ids.forEach { id -> Alarms.find(this, id)?.let { Alarms.cancel(this, it); Alarms.remove(this, id) } }
                Groups.remove(this, g.id); expGr = null; render()
            }
    }
    b.setNegativeButton("Annulla", null).show()
}

// ---------- Modifica gruppo: «Applica a tutte» ----------
// Ogni opzione ha la sua azione e cambia solo quella caratteristica: le altre restano com'erano su ogni sveglia.
// Non sono trasferibili ora, etichetta ed «Elimina dopo la suoneria».
private var pickGrId: String? = null

private fun grCount(g: Gr) = if (g.ids.size == 1) "1 sveglia" else "${g.ids.size} sveglie"

// Applica f a ogni sveglia del gruppo; quelle accese vengono ripianificate
private fun MainActivity.grApply(g: Gr, f: (Al) -> Unit) {
    var n = 0
    g.ids.forEach { id ->
        Alarms.find(this, id)?.let { a ->
            f(a); Alarms.put(this, a); if (a.on) Alarms.schedule(this, a); n++
        }
    }
    Toast.makeText(this, "Applicato a " + (if (n == 1) "1 sveglia" else "$n sveglie"), Toast.LENGTH_LONG).show()
    render()
}

private fun MainActivity.grEdit(g: Gr) {
    if (g.ids.isEmpty()) { Toast.makeText(this, "Il gruppo non ha sveglie", Toast.LENGTH_LONG).show(); return }
    val names = arrayOf("Giorni", "Suono", "Vibrazione", "Silenzia dopo", "Durata posticipo", "Volume crescente", "Avviso prima della sveglia")
    AlertDialog.Builder(this).setTitle("Applica a tutte (" + grCount(g) + ")").setItems(names) { _, w ->
        when (w) {
            0 -> grDays(g)
            1 -> grSound(g)
            2 -> grPick(g, "Vibrazione", listOf("Attiva", "Disattivata")) { a, i -> a.vib = (i == 0) }
            3 -> { val v = listOf(1, 5, 10, 15, 20, 25, 30, 0); grPick(g, "Silenzia dopo", v.map { silTxt(it) }) { a, i -> a.sil = v[i] } }
            4 -> { val v = listOf(1, 5, 10, 15, 20, 25, 30); grPick(g, "Durata posticipo", v.map { minTxt(it) }) { a, i -> a.snz = v[i] } }
            5 -> { val v = listOf(0, 5, 10, 15, 20, 30, 60); grPick(g, "Volume crescente", v.map { gradTxt(it) }) { a, i -> a.grad = v[i] } }
            else -> { val v = listOf(0, 15, 30, 60, 120, 180); grPick(g, "Avviso prima della sveglia", v.map { preTxt(it) }) { a, i -> a.pre = v[i] } }
        }
    }.setNegativeButton("Chiudi", null).show()
}

// Scelta singola: il valore parte vuoto e si applica solo premendo «Applica a tutte»
private fun MainActivity.grPick(g: Gr, title: String, labels: List<String>, set: (Al, Int) -> Unit) {
    var sel = -1
    AlertDialog.Builder(this).setTitle(title + " · " + grCount(g))
        .setSingleChoiceItems(labels.toTypedArray(), -1) { _, w -> sel = w }
        .setPositiveButton("Applica a tutte") { _, _ ->
            if (sel < 0) Toast.makeText(this, "Scegli un valore", Toast.LENGTH_LONG).show()
            else { val i = sel; grApply(g) { a -> set(a, i) } }
        }
        .setNegativeButton("Annulla", null).show()
}

// I giorni scelti sostituiscono quelli di ogni sveglia
private fun MainActivity.grDays(g: Gr) {
    val ord = order()
    val chk = BooleanArray(7)
    AlertDialog.Builder(this).setTitle("Giorni · " + grCount(g))
        .setMultiChoiceItems(ord.map { DAYN[it] }.toTypedArray(), chk) { _, i, v -> chk[i] = v }
        .setPositiveButton("Applica a tutte") { _, _ ->
            var d = 0
            ord.forEachIndexed { i, day -> if (chk[i]) d = d or (1 shl day) }
            if (d == 0) Toast.makeText(this, "Seleziona almeno un giorno", Toast.LENGTH_LONG).show()
            else grApply(g) { a -> a.days = d; a.skip = 0L }
        }
        .setNegativeButton("Annulla", null).show()
}

private fun MainActivity.grSound(g: Gr) {
    pickGrId = g.id
    val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    startActivityForResult(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, def)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true), 4)
}

// Chiamata da MainActivity quando il selettore di suoni del gruppo restituisce la scelta (u = "" per nessun suono)
fun MainActivity.groupSoundPicked(u: String) {
    val g = Groups.load(this).firstOrNull { it.id == pickGrId } ?: return
    AlertDialog.Builder(this).setTitle("Suono · " + grCount(g))
        .setMessage("«" + sndName(u) + "» sostituisce il suono di ogni sveglia del gruppo.")
        .setPositiveButton("Applica a tutte") { _, _ -> grApply(g) { a -> a.snd = u } }
        .setNegativeButton("Annulla", null).show()
}
