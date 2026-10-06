package it.timercent

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.*
import org.json.*
import java.time.ZonedDateTime
import java.util.Date

// days: bitmask, bit 0 = lunedì ... bit 6 = domenica; 0 = una sola volta
// snd: null = suono predefinito di sistema, "" = nessun suono, altrimenti URI
// skip: istante (ms) della suoneria saltata con "Salta"; le suonerie fino a quell'istante vengono ignorate
class Al(val id: String, var h: Int, var m: Int, var days: Int, var label: String, var on: Boolean,
         var vib: Boolean, var snd: String?, var del: Boolean, var skip: Long = 0L)

object Alarms {
    const val SNZ = "it.timercent.SNOOZE"
    const val SNZ_OFF = "it.timercent.SNOOZE_OFF"
    const val REFRESH = "it.timercent.REFRESH"
    const val END = "it.timercent.RING_END"
    const val PRE = "it.timercent.PRE"
    const val SKIP = "it.timercent.SKIP"

    fun p(c: Context) = c.getSharedPreferences("ct", 0)

    fun load(c: Context): MutableList<Al> {
        val a = JSONArray(p(c).getString("al", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Al(o.getString("id"), o.getInt("h"), o.getInt("m"), o.getInt("d"), o.optString("l"), o.optBoolean("on"),
                o.optBoolean("v", true), if (o.has("s")) o.getString("s") else null, o.optBoolean("x"), o.optLong("k", 0L))
        }
    }

    fun save(c: Context, l: List<Al>) {
        val a = JSONArray()
        l.forEach {
            val o = JSONObject().put("id", it.id).put("h", it.h).put("m", it.m).put("d", it.days).put("l", it.label)
                .put("on", it.on).put("v", it.vib).put("x", it.del).put("k", it.skip)
            if (it.snd != null) o.put("s", it.snd)
            a.put(o)
        }
        p(c).edit().putString("al", a.toString()).apply()
    }

    fun find(c: Context, id: String): Al? = load(c).firstOrNull { it.id == id }

    fun put(c: Context, a: Al) {
        val l = load(c)
        val i = l.indexOfFirst { it.id == a.id }
        if (i >= 0) l[i] = a else l.add(a)
        save(c, l)
    }

    fun remove(c: Context, id: String) { save(c, load(c).filter { it.id != id }) }

    fun nid(id: String) = id.hashCode() + 100
    fun npre(id: String) = id.hashCode() + 300

    fun weekStart(c: Context): Int =
        p(c).getInt("a_week", java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek.value)

    fun hm(c: Context, ms: Long): String = android.text.format.DateFormat.getTimeFormat(c).format(Date(ms))

    fun openTab(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 8, Intent(c, MainActivity::class.java).putExtra("tab", 3),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun refresh(c: Context) { c.sendBroadcast(Intent(REFRESH).setPackage(c.packageName)) }

    // Prossima suoneria strettamente successiva a "from"
    fun next(a: Al, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        for (i in 0..7) {
            val d = from.toLocalDate().plusDays(i.toLong())
            if (a.days != 0 && (a.days and (1 shl (d.dayOfWeek.value - 1))) == 0) continue
            val t = d.atTime(a.h, a.m).atZone(from.zone)
            if (t.isAfter(from)) return t
        }
        return from.plusDays(1)
    }

    // Come next(), ma ignora l'eventuale suoneria saltata
    fun nextEff(a: Al, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var t = next(a, from)
        var n = 0
        while (t.toInstant().toEpochMilli() <= a.skip && n++ < 10) t = next(a, t)
        return t
    }

    private fun pi(c: Context, a: Al, action: String? = null): PendingIntent {
        val i = Intent(c, WakeReceiver::class.java).putExtra("id", a.id)
        if (action != null) i.action = action
        return PendingIntent.getBroadcast(c, a.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancelPre(c: Context, a: Al) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, a, PRE))
        c.getSystemService(NotificationManager::class.java).cancel(npre(a.id))
    }

    fun schedule(c: Context, a: Al) {
        val am = c.getSystemService(AlarmManager::class.java)
        cancelPre(c, a)
        if (!a.on) { am.cancel(pi(c, a)); return }
        val t = nextEff(a).toInstant().toEpochMilli()
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t, openTab(c)), pi(c, a))
        // Notifica "prossima sveglia" N minuti prima (impostazione a_pre, 0 = mai)
        val pre = p(c).getInt("a_pre", 60)
        if (pre > 0) {
            val pt = t - pre * 60000L
            if (pt > System.currentTimeMillis()) {
                try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pt, pi(c, a, PRE)) }
                catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pt, pi(c, a, PRE)) }
            } else showUpcoming(c, a, t)
        }
    }

    // Notifica con conto alla rovescia e tasto "Salta" (per una sveglia singola equivale a disattivarla)
    fun showUpcoming(c: Context, a: Al, t: Long) {
        val now = System.currentTimeMillis()
        if (t <= now) return
        Notif.channels(c)
        val n = Notification.Builder(c, Notif.UP).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(a.label.ifEmpty { "Sveglia" } + " · " + hm(c, t)).setContentText("Prossima sveglia")
            .setWhen(t).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(t - now + 60000L)
            .setContentIntent(openTab(c)).addAction(0, "Salta", pi(c, a, SKIP)).build()
        c.getSystemService(NotificationManager::class.java).notify(npre(a.id), n)
    }

    fun cancelSnooze(c: Context, a: Al) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, a, SNZ))
        c.getSystemService(NotificationManager::class.java).cancel(nid(a.id))
    }

    fun cancel(c: Context, a: Al) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, a))
        cancelPre(c, a)
        cancelSnooze(c, a)
    }

    fun snooze(c: Context, a: Al): Long {
        val t = System.currentTimeMillis() + p(c).getInt("a_snz", 10) * 60000L
        c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(t, openTab(c)), pi(c, a, SNZ))
        return t
    }

    fun snoozeOffPi(c: Context, a: Al): PendingIntent = pi(c, a, SNZ_OFF)

    fun scheduleAll(c: Context) { load(c).filter { it.on }.forEach { schedule(c, it) } }
}

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val a = Alarms.find(c, id)
        val nm = c.getSystemService(NotificationManager::class.java)
        if (i.action == Alarms.PRE) {
            if (a != null && a.on) Alarms.showUpcoming(c, a, Alarms.nextEff(a).toInstant().toEpochMilli())
            return
        }
        if (i.action == Alarms.SKIP) {
            nm.cancel(Alarms.npre(id))
            if (a != null) {
                if (a.days == 0) {
                    a.on = false; Alarms.put(c, a); Alarms.cancel(c, a)
                    if (a.del) Alarms.remove(c, id)
                } else {
                    a.skip = Alarms.nextEff(a).toInstant().toEpochMilli(); Alarms.put(c, a); Alarms.schedule(c, a)
                }
            }
            Alarms.refresh(c); return
        }
        if (i.action == Alarms.SNZ_OFF) {
            if (a != null) {
                Alarms.cancelSnooze(c, a)
                if (!a.on && a.days == 0 && a.del) Alarms.remove(c, id)
            }
            Alarms.refresh(c); return
        }
        if (a == null) return
        if (i.action == Alarms.SNZ) {
            nm.cancel(Alarms.nid(id))
        } else {
            if (!a.on) return
            nm.cancel(Alarms.npre(id))
            if (a.days == 0) { a.on = false; Alarms.put(c, a) } else Alarms.schedule(c, a)
        }
        c.startForegroundService(Intent(c, WakeService::class.java).putExtra("id", id))
        Alarms.refresh(c)
    }
}

// Dopo riavvio, aggiornamento dell'app o cambio di ora/fuso: AlarmManager perde le sveglie, quindi si ripianificano
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Alarms.scheduleAll(c) } catch (e: Exception) { }
        try {
            AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, AnalogWidget::class.java)).forEach { Wg.refresh(c, it, false) }
        } catch (e: Exception) { }
        try {
            val now = System.currentTimeMillis()
            Store.load(c).filter { it.end > now }.forEach { Notif.start(c, it) }
        } catch (e: Exception) { }
    }
}

class WakeService : Service() {
    companion object { @Volatile var running = false }

    private var mp: MediaPlayer? = null
    private var cur: Al? = null
    private val h = Handler(Looper.getMainLooper())
    private val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    override fun onBind(i: Intent?): IBinder? = null

    private fun ringPi(label: String): PendingIntent = PendingIntent.getActivity(this, 20,
        Intent(this, RingActivity::class.java).putExtra("label", label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun act(a: String, rc: Int): PendingIntent = PendingIntent.getService(this, rc,
        Intent(this, WakeService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)

    // Sessione multimediale: l'orologio (es. Amazfit/Zepp) mostra i controlli musica del telefono;
    // "pausa"/"stop" fermano la sveglia, "traccia successiva" la posticipa
    private var ms: android.media.session.MediaSession? = null
    private fun session(label: String): android.media.session.MediaSession {
        ms?.let { return it }
        val s = android.media.session.MediaSession(this, "Timercent sveglia")
        s.setCallback(object : android.media.session.MediaSession.Callback() {
            override fun onPause() { end(false) }
            override fun onStop() { end(false) }
            override fun onSkipToNext() { end(true) }
        }, h)
        s.setPlaybackState(android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_PAUSE or android.media.session.PlaybackState.ACTION_PLAY_PAUSE
                or android.media.session.PlaybackState.ACTION_STOP or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)
            .setState(android.media.session.PlaybackState.STATE_PLAYING, 0L, 1f).build())
        s.setMetadata(android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, label.ifEmpty { "Sveglia" })
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Timercent").build())
        s.isActive = true
        ms = s
        return s
    }

    override fun onStartCommand(i: Intent?, f: Int, sid: Int): Int {
        when (i?.action) {
            "STOP" -> { end(false); return START_NOT_STICKY }
            "SNOOZE" -> { end(true); return START_NOT_STICKY }
        }
        running = true
        Notif.channels(this)
        val a = i?.getStringExtra("id")?.let { Alarms.find(this, it) }
        val label = a?.label ?: ""
        val snz = Alarms.p(this).getInt("a_snz", 10)
        val snzA = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
            "Posticipa ($snz min)", act("SNOOZE", 2)).build()
        val stopA = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Ferma", act("STOP", 3)).build()
        // Stesse due azioni anche per gli orologi Wear OS (quelli con app propria, come Zepp, le ignorano)
        val wear = Notification.WearableExtender().addAction(stopA).addAction(snzA)
        val n = Notification.Builder(this, Notif.WAKE).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label.ifEmpty { "Sveglia" }).setContentText(Alarms.hm(this, System.currentTimeMillis()))
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(ringPi(label), true).setContentIntent(ringPi(label))
            .addAction(snzA)
            .addAction(stopA)
            // Notifica "multimediale" legata alla sessione: i controlli musica dell'orologio agiscono sulla sveglia
            .setStyle(Notification.MediaStyle().setMediaSession(session(label).sessionToken).setShowActionsInCompactView(0, 1))
            .extend(wear).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(78, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(78, n)
        if (a == null) { stopSelf(); return START_NOT_STICKY }
        cur?.let { if (it.id != a.id) done(it, false, true) }
        cur = a
        ring(a)
        return START_NOT_STICKY
    }

    private fun play(u: Uri, v: Float): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attr); setDataSource(this@WakeService, u); isLooping = true
            prepare(); setVolume(v, v); start()
        }
    } catch (e: Exception) { null }

    private fun ring(a: Al) {
        h.removeCallbacksAndMessages(null)
        mp?.release(); mp = null
        val p = Alarms.p(this)
        val g = p.getInt("a_grad", 0)
        val v0 = if (g > 0) 0f else 1f
        if (a.snd != "") {
            val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val first = a.snd?.let { Uri.parse(it) } ?: def
            mp = play(first, v0) ?: (if (first != def) play(def, v0) else null)
        }
        val m = mp
        if (m != null && g > 0) {
            val t0 = SystemClock.elapsedRealtime()
            h.post(object : Runnable {
                override fun run() {
                    val v = ((SystemClock.elapsedRealtime() - t0) / (g * 1000f)).coerceAtMost(1f)
                    try { m.setVolume(v, v) } catch (e: Exception) { return }
                    if (v < 1f) h.postDelayed(this, 100)
                }
            })
        }
        if (a.vib) {
            try { getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0), attr) } catch (e: Exception) { }
        }
        val s = p.getInt("a_sil", 10)
        if (s > 0) h.postDelayed({ end(false, true) }, s * 60000L)
    }

    // Esito della suoneria: posticipata (nuova sveglia tra N minuti), fermata, oppure persa
    private fun done(a: Al, snooze: Boolean, missed: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (snooze) {
            val t = Alarms.snooze(this, a)
            nm.notify(Alarms.nid(a.id), Notification.Builder(this, Notif.RUN).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(a.label.ifEmpty { "Sveglia" }).setContentText("Posticipata fino alle " + Alarms.hm(this, t))
                .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setContentIntent(Alarms.openTab(this))
                .addAction(0, "Ferma", Alarms.snoozeOffPi(this, a)).build())
        } else {
            if (a.days == 0 && a.del) Alarms.remove(this, a.id)
            if (missed) nm.notify(Alarms.nid(a.id), Notification.Builder(this, Notif.MISS).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sveglia mancata").setContentText((a.label.ifEmpty { "Sveglia" }) + " · " + Alarms.hm(this, System.currentTimeMillis()))
                .setAutoCancel(true).setContentIntent(Alarms.openTab(this)).build())
        }
    }

    // Chiude la sessione multimediale dicendo prima all'orologio che la riproduzione è finita (STATE_STOPPED):
    // se la sessione sparisce mentre risulta ancora "in riproduzione", Zepp resta bloccato sulla schermata musica
    private fun closeSession() {
        val s = ms ?: return
        ms = null
        try {
            s.setPlaybackState(android.media.session.PlaybackState.Builder().setActions(0L)
                .setState(android.media.session.PlaybackState.STATE_STOPPED, 0L, 0f).build())
            s.setMetadata(null)
        } catch (e: Exception) { }
        // piccola attesa perché l'aggiornamento arrivi all'orologio prima di rilasciare la sessione
        Handler(Looper.getMainLooper()).postDelayed({
            try { s.isActive = false; s.release() } catch (e: Exception) { }
        }, 1500)
    }

    private fun end(snooze: Boolean, missed: Boolean = false) {
        cur?.let { done(it, snooze, missed) }
        cur = null
        closeSession()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(Alarms.END).setPackage(packageName))
        Alarms.refresh(this)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        mp?.release(); mp = null
        closeSession()
        try { getSystemService(Vibrator::class.java).cancel() } catch (e: Exception) { }
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

// Schermata a tutto schermo mostrata anche a telefono bloccato
class RingActivity : Activity() {
    private var reg = false
    private val endRx = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { finish() } }
    private fun act(a: String) { startService(Intent(this, WakeService::class.java).setAction(a)) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (!WakeService.running) { finish(); return }
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = BG; window.navigationBarColor = BG
        volumeControlStream = AudioManager.STREAM_ALARM
        val f = IntentFilter(Alarms.END)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(endRx, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(endRx, f)
        reg = true

        val den = resources.displayMetrics.density
        fun d(x: Int) = (x * den).toInt()
        fun btn(s: String, bg: Int, fg: Int, f: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; textSize = 20f; setTextColor(fg); minHeight = d(72)
            background = GradientDrawable().apply { setColor(bg); cornerRadius = d(20).toFloat() }
            setOnClickListener { f() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(BG); setPadding(d(24), d(72), d(24), d(40))
        }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 80f; setTextColor(FG)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER
        }
        val label = TextView(this).apply {
            text = intent.getStringExtra("label")?.ifEmpty { null } ?: "Sveglia"
            textSize = 24f; setTextColor(ACC); gravity = Gravity.CENTER
        }
        val snz = Alarms.p(this).getInt("a_snz", 10)
        root.addView(clock); root.addView(label)
        root.addView(Space(this), LinearLayout.LayoutParams(0, 0, 1f))
        root.addView(btn("Posticipa di $snz min", CARD, FG) { act("SNOOZE") },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(16) })
        root.addView(btn("Ferma", ACC, DARK) { act("STOP") }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if ((k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            when (Alarms.p(this).getInt("a_btn", 0)) {
                0 -> { if (e.repeatCount == 0) act("SNOOZE"); return true }
                1 -> { if (e.repeatCount == 0) act("STOP"); return true }
            }
        }
        return super.onKeyDown(k, e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { }   // la sveglia si chiude solo con Posticipa o Ferma

    override fun onDestroy() {
        if (reg) try { unregisterReceiver(endRx) } catch (e: Exception) { }
        super.onDestroy()
    }
}
