package it.cronotimer

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import org.json.*

class T(val id: String, var label: String, var ms: Long, var left: Long, var end: Long)

object Store {
    fun load(c: Context): MutableList<T> {
        val a = JSONArray(c.getSharedPreferences("ct", 0).getString("l", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            T(o.getString("id"), o.getString("label"), o.getLong("ms"), o.getLong("left"), o.getLong("end"))
        }
    }
    fun save(c: Context, l: List<T>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("label", it.label).put("ms", it.ms).put("left", it.left).put("end", it.end)) }
        c.getSharedPreferences("ct", 0).edit().putString("l", a.toString()).apply()
    }
}

object Notif {
    const val RUN = "run"
    const val RING = "ring"
    fun channels(c: Context) {
        val m = c.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(RUN, "Timer in corso", NotificationManager.IMPORTANCE_LOW))
        val e = NotificationChannel(RING, "Timer finito", NotificationManager.IMPORTANCE_HIGH)
        e.setSound(null, null); e.enableVibration(false)
        m.createNotificationChannel(e)
    }
    fun sound(c: Context): Uri =
        c.getSharedPreferences("ct", 0).getString("snd", null)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    fun halt(c: Context) { c.stopService(Intent(c, AlarmService::class.java)) }
    fun open(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    private fun alarmPi(c: Context, t: T): PendingIntent =
        PendingIntent.getBroadcast(c, t.id.hashCode(),
            Intent(c, AlarmReceiver::class.java).putExtra("id", t.id).putExtra("label", t.label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun start(c: Context, t: T) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.end, alarmPi(c, t))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.end, alarmPi(c, t))
        val n = Notification.Builder(c, RUN).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(t.label.ifEmpty { "Timer" }).setWhen(t.end).setUsesChronometer(true)
            .setChronometerCountDown(true).setOngoing(true).setContentIntent(open(c)).build()
        c.getSystemService(NotificationManager::class.java).notify(t.id.hashCode(), n)
    }
    fun stop(c: Context, t: T) {
        c.getSystemService(AlarmManager::class.java).cancel(alarmPi(c, t))
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.cancel(t.id.hashCode()); nm.cancel(t.id.hashCode() + 1)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        c.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
        c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("label", i.getStringExtra("label") ?: ""))
    }
}

class AlarmService : Service() {
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        Notif.channels(this)
        val stop = PendingIntent.getService(this, 1, Intent(this, AlarmService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, Notif.RING).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle((i?.getStringExtra("label") ?: "").ifEmpty { "Timer" }).setContentText("Tempo scaduto")
            .setContentIntent(Notif.open(this)).setCategory(Notification.CATEGORY_ALARM)
            .addAction(0, "Ferma", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(77, n)
        val alarm = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        mp?.release(); mp = null
        try {
            mp = MediaPlayer().apply { setAudioAttributes(alarm); setDataSource(this@AlarmService, Notif.sound(this@AlarmService)); isLooping = true; prepare(); start() }
        } catch (e: Exception) { }
        try {
            getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0), alarm)
        } catch (e: Exception) { }
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ stopSelf() }, 60000)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        mp?.release(); mp = null
        try { getSystemService(Vibrator::class.java).cancel() } catch (e: Exception) { }
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
