package it.cronotimer

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
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
    const val END = "end"
    fun channels(c: Context) {
        val m = c.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(RUN, "Timer in corso", NotificationManager.IMPORTANCE_LOW))
        val e = NotificationChannel(END, "Timer finito", NotificationManager.IMPORTANCE_HIGH)
        e.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        e.enableVibration(true)
        m.createNotificationChannel(e)
    }
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
        Notif.channels(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        val id = i.getStringExtra("id") ?: return
        val label = (i.getStringExtra("label") ?: "").ifEmpty { "Timer" }
        nm.cancel(id.hashCode())
        val n = Notification.Builder(c, Notif.END).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label).setContentText("Tempo scaduto").setContentIntent(Notif.open(c))
            .setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(id.hashCode() + 1, n)
    }
}
