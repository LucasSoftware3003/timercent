package it.timercent

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Impostazioni di un singolo widget.
class WC(
    var bg: Int = 0,            // 0 opaco, 1 semitrasparente, 2 trasparente
    var tSize: Int = 56,        // sp
    var tCol: Int = Color.parseColor("#EEF0F3"),
    var sep: Int = 0,           // indice in Wg.SEPS
    var stack: Boolean = false, // false in linea, true ore sopra e minuti sotto
    var dOn: Boolean = true,
    var dBottom: Boolean = true,
    var dFmt: Int = 0,          // indice in Wg.DATE_FMT
    var dSize: Int = 16,        // sp
    var dCol: Int = Color.parseColor("#8A93A0")
)

object Wg {
    val ALPHA = intArrayOf(240, 110, 0)
    val SEPS = arrayOf(":", ".", ",", " ")
    val SEP_LBL = listOf("Due punti  :", "Punto  .", "Virgola  ,", "Spazio")
    val DATE_FMT = arrayOf("EEEE d MMMM", "EEE d MMM", "d MMMM yyyy", "dd/MM/yyyy", "dd/MM/yy", "EEE dd/MM", "d MMM", "yyyy-MM-dd", "EEE d MMM yyyy", "EEEE d MMMM yyyy", "EEE dd/MM/yy")
    val PAL: IntArray = listOf("#EEF0F3", "#000000", "#8A93A0", "#FFB020", "#FF7043", "#F44336",
        "#EC407A", "#AB47BC", "#42A5F5", "#26C6DA", "#66BB6A", "#D4E157").map { Color.parseColor(it) }.toIntArray()
    private val KEYS = listOf("bg", "ts", "tc", "sp", "st", "do", "db", "df", "ds", "dc")

    private fun p(c: Context) = c.getSharedPreferences("ct", 0)

    fun load(c: Context, id: Int, digital: Boolean): WC {
        val s = p(c); val d = WC()
        return WC(
            bg = s.getInt("bg$id", 0).coerceIn(0, 2),
            tSize = s.getInt("ts$id", d.tSize),
            tCol = s.getInt("tc$id", d.tCol),
            sep = s.getInt("sp$id", 0).coerceIn(0, SEPS.size - 1),
            stack = s.getBoolean("st$id", false),
            dOn = s.getBoolean("do$id", digital),
            dBottom = s.getBoolean("db$id", true),
            dFmt = s.getInt("df$id", 0).coerceIn(0, DATE_FMT.size - 1),
            dSize = s.getInt("ds$id", if (digital) 16 else 12),
            dCol = s.getInt("dc$id", d.dCol)
        )
    }

    fun save(c: Context, id: Int, k: WC) {
        p(c).edit()
            .putInt("bg$id", k.bg).putInt("ts$id", k.tSize).putInt("tc$id", k.tCol)
            .putInt("sp$id", k.sep).putBoolean("st$id", k.stack).putBoolean("do$id", k.dOn)
            .putBoolean("db$id", k.dBottom).putInt("df$id", k.dFmt).putInt("ds$id", k.dSize)
            .putInt("dc$id", k.dCol).apply()
    }

    fun forget(c: Context, ids: IntArray) {
        val e = p(c).edit()
        ids.forEach { id -> KEYS.forEach { key -> e.remove("$key$id") } }
        e.apply()
    }

    private fun txt(v: RemoteViews, id: Int, size: Int, col: Int) {
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        v.setTextColor(id, col)
    }

    private fun fmt(v: RemoteViews, id: Int, f12: String, f24: String) {
        v.setCharSequence(id, "setFormat12Hour", f12)
        v.setCharSequence(id, "setFormat24Hour", f24)
    }

    fun refresh(c: Context, id: Int, digital: Boolean) {
        val k = load(c, id, digital)
        val v = RemoteViews(c.packageName, if (digital) R.layout.widget_digital else R.layout.widget_analog)
        v.setInt(R.id.bg, "setImageAlpha", ALPHA[k.bg])

        // Data: sopra o sotto, formato, dimensione e colore.
        v.setViewVisibility(R.id.date_top, if (k.dOn && !k.dBottom) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.date_bottom, if (k.dOn && k.dBottom) View.VISIBLE else View.GONE)
        for (d in intArrayOf(R.id.date_top, R.id.date_bottom)) {
            fmt(v, d, DATE_FMT[k.dFmt], DATE_FMT[k.dFmt]); txt(v, d, k.dSize, k.dCol)
        }

        if (digital) {
            val s = SEPS[k.sep]
            v.setViewVisibility(R.id.t_inline, if (k.stack) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.t_stack, if (k.stack) View.VISIBLE else View.GONE)
            fmt(v, R.id.t_inline, "h'$s'mm", "HH'$s'mm"); txt(v, R.id.t_inline, k.tSize, k.tCol)
            fmt(v, R.id.t_hour, "h", "HH"); txt(v, R.id.t_hour, k.tSize, k.tCol)
            fmt(v, R.id.t_min, "mm", "mm"); txt(v, R.id.t_min, k.tSize, k.tCol)
        }
        v.setOnClickPendingIntent(R.id.root, Notif.openTimer(c))
        AppWidgetManager.getInstance(c).updateAppWidget(id, v)
    }
}

// TextClock e AnalogClock sono aggiornati dal sistema: qui bastano impostazioni e tocco.
open class ClockBase(private val digital: Boolean) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { Wg.refresh(c, it, digital) } }
    override fun onDeleted(c: Context, ids: IntArray) { Wg.forget(c, ids) }
}

class DigitalWidget : ClockBase(true)
class AnalogWidget : ClockBase(false)

// Si apre quando aggiungi il widget e dal "Riconfigura" del launcher.
class WidgetConfig : Activity() {
    private val FG = Color.parseColor("#EEF0F3")
    private val MUTE = Color.parseColor("#8A93A0")
    private val ACC = Color.parseColor("#FFB020")
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun title(box: LinearLayout, t: String) {
        box.addView(TextView(this).apply {
            text = t; setTextColor(ACC); textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(2))
        })
    }

    private fun label(box: LinearLayout, t: String) {
        box.addView(TextView(this).apply { text = t; setTextColor(MUTE); textSize = 13f; setPadding(0, dp(10), 0, 0) })
    }

    private fun spin(box: LinearLayout, lbl: String, items: List<String>, cur: Int, on: (Int) -> Unit): Spinner {
        label(box, lbl)
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        s.setSelection(cur)
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, i: Long) { on(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        box.addView(s)
        return s
    }

    private fun seek(box: LinearLayout, lbl: String, lo: Int, hi: Int, cur: Int, on: (Int) -> Unit) {
        val t = TextView(this).apply { setTextColor(MUTE); textSize = 13f; setPadding(0, dp(10), 0, 0) }
        t.text = "$lbl: $cur sp"
        val s = SeekBar(this)
        s.max = hi - lo
        s.progress = cur - lo
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { t.text = "$lbl: ${p + lo} sp"; on(p + lo) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(t); box.addView(s)
    }

    private fun colors(box: LinearLayout, lbl: String, cur: Int, on: (Int) -> Unit) {
        label(box, lbl)
        val row = LinearLayout(this).apply { setPadding(0, dp(6), 0, dp(2)) }
        val views = ArrayList<View>()
        var sel = cur
        fun paint() {
            views.forEachIndexed { i, v ->
                val chosen = Wg.PAL[i] == sel
                val edge = if (Color.luminance(Wg.PAL[i]) > 0.5f) Color.BLACK else Color.WHITE
                (v.background as GradientDrawable).setStroke(dp(if (chosen) 3 else 1), if (chosen) edge else 0x55FFFFFF)
            }
        }
        Wg.PAL.forEachIndexed { i, col ->
            val v = View(this)
            v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }
            v.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) }
            v.setOnClickListener { sel = Wg.PAL[i]; paint(); on(sel) }
            views.add(v); row.addView(v)
        }
        paint()
        box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setResult(RESULT_CANCELED)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val digital = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className?.endsWith("DigitalWidget") == true
        val k = Wg.load(this, id, digital)

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        box.addView(TextView(this).apply {
            text = if (digital) "Orologio digitale" else "Orologio analogico"
            setTextColor(FG); textSize = 22f; setPadding(0, dp(8), 0, 0)
        })

        title(box, "Sfondo")
        spin(box, "Trasparenza", listOf("Opaco", "Semitrasparente", "Trasparente"), k.bg) { k.bg = it }

        if (digital) {
            title(box, "Ora")
            seek(box, "Dimensione", 20, 120, k.tSize) { k.tSize = it }
            colors(box, "Colore", k.tCol) { k.tCol = it }
            val sepSpin = spin(box, "Separatore ore e minuti", Wg.SEP_LBL, k.sep) { k.sep = it }
            spin(box, "Disposizione", listOf("In linea", "Ore sopra, minuti sotto"), if (k.stack) 1 else 0) {
                k.stack = it == 1; sepSpin.isEnabled = !k.stack; sepSpin.alpha = if (k.stack) 0.4f else 1f
            }
            sepSpin.isEnabled = !k.stack; sepSpin.alpha = if (k.stack) 0.4f else 1f
        }

        title(box, "Data")
        val dBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sw = Switch(this).apply {
            text = "Mostra la data"; setTextColor(FG); isChecked = k.dOn
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, on -> k.dOn = on; dBox.visibility = if (on) View.VISIBLE else View.GONE }
        }
        box.addView(sw)
        dBox.visibility = if (k.dOn) View.VISIBLE else View.GONE
        spin(dBox, "Posizione", listOf("Sopra", "Sotto"), if (k.dBottom) 1 else 0) { k.dBottom = it == 1 }
        val now = Date()
        val ex = Wg.DATE_FMT.map { SimpleDateFormat(it, Locale.getDefault()).format(now) }
        spin(dBox, "Formato", ex, k.dFmt) { k.dFmt = it }
        seek(dBox, "Dimensione", 8, 40, k.dSize) { k.dSize = it }
        colors(dBox, "Colore", k.dCol) { k.dCol = it }
        box.addView(dBox)

        box.addView(Button(this).apply {
            text = "Salva"
            setOnClickListener {
                Wg.save(this@WidgetConfig, id, k); Wg.refresh(this@WidgetConfig, id, digital)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(28) }
        })
        setContentView(ScrollView(this).apply { addView(box) })
    }
}
