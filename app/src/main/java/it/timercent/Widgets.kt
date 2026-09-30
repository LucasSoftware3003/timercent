package it.timercent

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

// TextClock e AnalogClock sono aggiornati dal sistema: qui basta collegare il tocco all'app.
open class ClockBase(private val layout: Int) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val v = RemoteViews(c.packageName, layout)
        v.setOnClickPendingIntent(R.id.root, Notif.open(c))
        ids.forEach { m.updateAppWidget(it, v) }
    }
}

class DigitalWidget : ClockBase(R.layout.widget_digital)
class AnalogWidget : ClockBase(R.layout.widget_analog)
