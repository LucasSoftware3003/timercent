package it.timercent

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

// Sfondo scelto per ogni widget: 0 opaco, 1 semitrasparente, 2 trasparente.
object Wg {
    private val ALPHA = intArrayOf(240, 110, 0)
    private val ANALOG = intArrayOf(R.layout.widget_analog_0, R.layout.widget_analog_1, R.layout.widget_analog_2)
    private fun p(c: Context) = c.getSharedPreferences("ct", 0)
    fun mode(c: Context, id: Int) = p(c).getInt("bg$id", 0).coerceIn(0, 2)
    fun set(c: Context, id: Int, m: Int) { p(c).edit().putInt("bg$id", m).apply() }
    fun forget(c: Context, ids: IntArray) { p(c).edit().apply { ids.forEach { remove("bg$it") } }.apply() }
    fun refresh(c: Context, id: Int, digital: Boolean) {
        val m = mode(c, id)
        val v = if (digital) RemoteViews(c.packageName, R.layout.widget_digital).apply { setInt(R.id.bg, "setImageAlpha", ALPHA[m]) }
                else RemoteViews(c.packageName, ANALOG[m])
        v.setOnClickPendingIntent(R.id.root, Notif.open(c))
        AppWidgetManager.getInstance(c).updateAppWidget(id, v)
    }
}

// TextClock e AnalogClock sono aggiornati dal sistema: qui basta sfondo e tocco.
open class ClockBase(private val digital: Boolean) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { Wg.refresh(c, it, digital) } }
    override fun onDeleted(c: Context, ids: IntArray) { Wg.forget(c, ids) }
}

class DigitalWidget : ClockBase(true)
class AnalogWidget : ClockBase(false)

// Si apre quando aggiungi il widget e dal "Riconfigura" del launcher.
class WidgetConfig : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setResult(RESULT_CANCELED)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val digital = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className?.endsWith("DigitalWidget") == true
        AlertDialog.Builder(this).setTitle("Sfondo del widget")
            .setSingleChoiceItems(arrayOf("Opaco", "Semitrasparente", "Trasparente"), Wg.mode(this, id)) { d, w ->
                Wg.set(this, id, w); Wg.refresh(this, id, digital)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                d.dismiss(); finish()
            }
            .setOnCancelListener { finish() }.show()
    }
}
