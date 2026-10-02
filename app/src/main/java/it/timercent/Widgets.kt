package it.timercent

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
    var dCol: Int = Color.parseColor("#8A93A0"),
    var font: Int = 0,          // indice in Wg.FONT_FAM
    var dial: String = "p0",     // quadrante (vedi Dials)
    var hand: Int = 0,          // schema colore lancette (Wg.HAND_LBL)
    var photo: String? = null,  // foto di sfondo (file in Dials.dir)
    var dim: Int = 0,           // scurimento della foto
    var zone: String = "",      // id del fuso (vuoto = ora locale)
    var zlbl: Boolean = true,   // nome della città accanto alla data
    var crop: Boolean = false   // foto di sfondo: false ridimensiona (riempie), true centra e ritaglia
)

object Wg {
    val ALPHA = intArrayOf(240, 110, 0)
    val SEPS = arrayOf(":", ".", ",", " ")
    val SEP_LBL = listOf("Due punti  :", "Punto  .", "Virgola  ,", "Spazio")
    val DATE_FMT = arrayOf("EEEE d MMMM", "EEE d MMM", "d MMMM yyyy", "dd/MM/yyyy", "dd/MM/yy", "EEE dd/MM", "d MMM", "yyyy-MM-dd", "EEE d MMM yyyy", "EEEE d MMMM yyyy", "EEE dd/MM/yy")
    val PAL: IntArray = listOf("#EEF0F3", "#000000", "#8A93A0", "#FFB020", "#FF7043", "#F44336",
        "#EC407A", "#AB47BC", "#42A5F5", "#26C6DA", "#66BB6A", "#D4E157").map { Color.parseColor(it) }.toIntArray()
    val HAND_LBL = listOf("Classico (bianca e ambra)", "Bianco", "Nero", "Grigio", "Ambra", "Arancio", "Rosso", "Rosa", "Viola", "Azzurro", "Turchese", "Verde", "Lime")
    // Colori per sfondo, bordo, numeri e tacche del quadrante
    val DPAL: IntArray = listOf("#000000", "#14181F", "#1B2430", "#2B3A55", "#37474F", "#4E342E", "#1B5E20", "#880E4F",
        "#F5F1E6", "#EEF0F3", "#FFFFFF", "#FFB020", "#FF7043", "#42A5F5", "#26C6DA", "#66BB6A").map { Color.parseColor(it) }.toIntArray()
    val FONT_LBL = listOf("Predefinito (leggero)", "Normale", "Medio", "Nero", "Condensato", "Serif", "Monospace", "Corsivo")
    val FONT_FAM = arrayOf("sans-serif-light", "sans-serif", "sans-serif-medium", "sans-serif-black", "sans-serif-condensed", "serif", "monospace", "cursive")
    // Un layout per carattere: nei widget il carattere si sceglie solo nell'XML. Il numero 0 è il layout originale.
    private val DIG = intArrayOf(R.layout.widget_digital, R.layout.widget_digital_f1, R.layout.widget_digital_f2, R.layout.widget_digital_f3,
        R.layout.widget_digital_f4, R.layout.widget_digital_f5, R.layout.widget_digital_f6, R.layout.widget_digital_f7)
    private val KEYS = listOf("bg", "ts", "tc", "sp", "st", "do", "db", "df", "ds", "dc", "fn", "dl", "hc", "ph", "pd", "zn", "zl", "cr")

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
            dCol = s.getInt("dc$id", d.dCol),
            font = s.getInt("fn$id", 0).coerceIn(0, FONT_FAM.size - 1),
            dial = s.getString("dl$id", "p0") ?: "p0",
            hand = s.getInt("hc$id", 0).coerceIn(0, HAND_LBL.size - 1),
            photo = s.getString("ph$id", null),
            dim = s.getInt("pd$id", 0).coerceIn(0, 3),
            zone = s.getString("zn$id", "") ?: "",
            zlbl = s.getBoolean("zl$id", true),
            crop = s.getBoolean("cr$id", false)
        )
    }

    fun save(c: Context, id: Int, k: WC) {
        p(c).edit()
            .putInt("bg$id", k.bg).putInt("ts$id", k.tSize).putInt("tc$id", k.tCol)
            .putInt("sp$id", k.sep).putBoolean("st$id", k.stack).putBoolean("do$id", k.dOn)
            .putBoolean("db$id", k.dBottom).putInt("df$id", k.dFmt).putInt("ds$id", k.dSize)
            .putInt("dc$id", k.dCol).putInt("fn$id", k.font).putString("dl$id", k.dial).putInt("hc$id", k.hand).putString("ph$id", k.photo).putInt("pd$id", k.dim).putString("zn$id", k.zone).putBoolean("zl$id", k.zlbl).putBoolean("cr$id", k.crop).apply()
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

    // Dimensioni attuali del widget in dp (secondo l'orientamento); 0 se non ancora note
    fun sizeDp(c: Context, id: Int): Pair<Int, Int> {
        val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
        val land = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return Pair(
            o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
            o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0))
    }

    // L'AnalogClock accetta il fuso nei widget solo se il metodo è esposto alle RemoteViews (Android 12+)
    fun analogTzOk(): Boolean = try {
        android.widget.AnalogClock::class.java.getMethod("setTimeZone", String::class.java)
            .annotations.any { it.annotationClass.java.simpleName == "RemotableViewMethod" }
    } catch (e: Throwable) { false }

    fun refresh(c: Context, id: Int, digital: Boolean) {
        val k = load(c, id, digital)
        val v = RemoteViews(c.packageName, if (digital) DIG[k.font] else AnaRes.LAYOUTS[k.font * AnaRes.HANDS + k.hand])
        v.setInt(R.id.bg, "setImageAlpha", ALPHA[k.bg])
        v.setInt(R.id.bg_crop, "setImageAlpha", ALPHA[k.bg])
        v.setViewVisibility(R.id.bg, View.VISIBLE)
        v.setViewVisibility(R.id.bg_crop, View.GONE)
        val ph = k.photo
        if (ph != null) {
            try {
                Dials.photoBg(c, id, ph, k.dim)?.let {
                    // Due immagini sovrapposte nel layout: una stira (fitXY), l'altra centra e ritaglia (centerCrop)
                    if (k.crop) {
                        v.setImageViewBitmap(R.id.bg_crop, it)
                        v.setViewVisibility(R.id.bg_crop, View.VISIBLE)
                        v.setViewVisibility(R.id.bg, View.GONE)
                    } else v.setImageViewBitmap(R.id.bg, it)
                }
            } catch (e: Exception) { }
        }

        // Data: sopra o sotto, formato, dimensione e colore.
        v.setViewVisibility(R.id.date_top, if (k.dOn && !k.dBottom) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.date_bottom, if (k.dOn && k.dBottom) View.VISIBLE else View.GONE)
        // Fuso orario scelto: sull'analogico solo se il sistema lo permette, altrimenti ora e data resterebbero discordi
        val useTz = k.zone.isNotEmpty() && (digital || analogTzOk())
        val lbl = if (useTz && k.zlbl) " '· " + zoneCity(k.zone).replace("'", "''") + "'" else ""
        for (d in intArrayOf(R.id.date_top, R.id.date_bottom)) {
            fmt(v, d, DATE_FMT[k.dFmt] + lbl, DATE_FMT[k.dFmt] + lbl); txt(v, d, k.dSize, k.dCol)
            v.setString(d, "setTimeZone", if (useTz) k.zone else null)
        }

        if (digital) {
            val s = SEPS[k.sep]
            v.setViewVisibility(R.id.t_inline, if (k.stack) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.t_stack, if (k.stack) View.VISIBLE else View.GONE)
            fmt(v, R.id.t_inline, "h'$s'mm", "HH'$s'mm"); txt(v, R.id.t_inline, k.tSize, k.tCol)
            fmt(v, R.id.t_hour, "h", "HH"); txt(v, R.id.t_hour, k.tSize, k.tCol)
            fmt(v, R.id.t_min, "mm", "mm"); txt(v, R.id.t_min, k.tSize, k.tCol)
            for (t in intArrayOf(R.id.t_inline, R.id.t_hour, R.id.t_min)) v.setString(t, "setTimeZone", if (useTz) k.zone else null)
        }
        else {
            try { v.setImageViewBitmap(R.id.dial_img, Dials.bitmap(c, k.dial)) } catch (e: Exception) { }
            // Il quadrante è un quadrato centrato: si restringe l'area del contenuto alla sua misura,
            // così la data resta attaccata al quadrante invece che al bordo del widget.
            if (analogTzOk()) v.setString(R.id.analog, "setTimeZone", if (useTz) k.zone else java.util.TimeZone.getDefault().id)
            val (wd, hd) = sizeDp(c, id)
            if (wd > 0 && hd > 0) {
                val den = c.resources.displayMetrics.density
                val dh = if (k.dOn) k.dSize * 1.2f + 2f else 0f
                val sq = minOf(wd - 8f, hd - 8f - dh).coerceAtLeast(10f)
                val pv = maxOf(4f, (hd - sq - dh) / 2f)
                v.setViewPadding(R.id.content, (4 * den).toInt(), (pv * den).toInt(), (4 * den).toInt(), (pv * den).toInt())
            }
        }
        v.setOnClickPendingIntent(R.id.root, Notif.openTimer(c))
        AppWidgetManager.getInstance(c).updateAppWidget(id, v)
    }
}

// TextClock e AnalogClock sono aggiornati dal sistema: qui bastano impostazioni e tocco.
open class ClockBase(private val digital: Boolean) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { Wg.refresh(c, it, digital) } }
    override fun onDeleted(c: Context, ids: IntArray) { Wg.forget(c, ids) }
    // Ridimensionando il widget la foto va ritagliata di nuovo
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle?) {
        super.onAppWidgetOptionsChanged(c, m, id, o)
        Wg.refresh(c, id, digital)
    }
}

class DigitalWidget : ClockBase(true)
class AnalogWidget : ClockBase(false)

// Si apre quando aggiungi il widget e dal "Riconfigura" del launcher.
class WidgetConfig : CfgBase() {
    private var k = WC()
    private lateinit var dialSpin: Spinner
    private lateinit var dialPrev: ImageView
    private var dialList: List<DialSpec> = emptyList()
    private lateinit var photoInfo: TextView
    private lateinit var zoneInfo: TextView

    private fun infoZ() {
        zoneInfo.text = if (k.zone.isEmpty()) "Fuso: ora locale del telefono" else "Fuso: " + zoneCity(k.zone) + " (" + zoneGmt(k.zone) + ")"
    }

    private fun infoPh() { photoInfo.text = if (k.photo != null) "Foto di sfondo: impostata" else "Foto di sfondo: nessuna" }

    private fun updPrev() { dialPrev.setImageBitmap(Dials.preview(this, Dials.find(this, k.dial), k.hand)) }

    private fun fillDials() {
        dialList = Dials.all(this)
        dialSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dialList.map { it.name })
        dialSpin.setSelection(dialList.indexOfFirst { it.id == k.dial }.coerceAtLeast(0))
        updPrev()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rq == 6 && rs == RESULT_OK && d?.data != null) {
            val n = Dials.importPhoto(this, d.data!!)
            if (n != null) { k.photo = n; infoPh() } else android.widget.Toast.makeText(this, "Immagine non valida", android.widget.Toast.LENGTH_LONG).show()
        }
        if (rq == 5) {
            if (rs == RESULT_OK) d?.getStringExtra("dial")?.let { k.dial = it }
            fillDials()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setResult(RESULT_CANCELED)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val digital = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className?.endsWith("DigitalWidget") == true
        k = Wg.load(this, id, digital)

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        box.addView(TextView(this).apply {
            text = if (digital) "Orologio digitale" else "Orologio analogico"
            setTextColor(FG); textSize = 22f; setPadding(0, dp(8), 0, 0)
        })

        title(box, "Sfondo")
        spin(box, "Trasparenza", listOf("Opaco", "Semitrasparente", "Trasparente"), k.bg) { k.bg = it }
        photoInfo = TextView(this)
        photoInfo.setTextColor(MUTE); photoInfo.textSize = 13f; photoInfo.setPadding(0, dp(12), 0, 0)
        box.addView(photoInfo)
        infoPh()
        val pr = LinearLayout(this)
        val pb1 = Button(this); pb1.text = "Scegli foto…"; pb1.isAllCaps = false
        pb1.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 6)
        }
        val pb2 = Button(this); pb2.text = "Rimuovi foto"; pb2.isAllCaps = false
        pb2.setOnClickListener { k.photo = null; infoPh() }
        pr.addView(pb1, LinearLayout.LayoutParams(0, -2, 1f)); pr.addView(pb2, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(pr)
        spin(box, "Scurisci la foto (per leggere meglio)", Dials.DIM_LBL, k.dim) { k.dim = it }
        spin(box, "Adattamento della foto", listOf("Ridimensiona (riempie il widget)", "Centra e ritaglia (non si deforma)"), if (k.crop) 1 else 0) { k.crop = it == 1 }
        note(box, "Ridimensiona: la foto riempie sempre tutto il widget, ma può deformarsi se ridimensioni il widget. " +
            "Centra e ritaglia: la foto mantiene le proporzioni e al massimo perde un po' dei bordi.")
        note(box, if (digital) "Questa foto copre tutto il widget, dietro l'ora."
            else "Questa foto copre tutto il widget, dietro il quadrante. La foto dentro il quadrante si sceglie invece in «Crea o modifica quadrante».")

        title(box, "Città / fuso orario")
        zoneInfo = TextView(this)
        zoneInfo.setTextColor(FG); zoneInfo.textSize = 15f; zoneInfo.setPadding(0, dp(10), 0, 0)
        box.addView(zoneInfo)
        infoZ()
        val zr = LinearLayout(this)
        val zb1 = Button(this); zb1.text = "Scegli città…"; zb1.isAllCaps = false
        zb1.setOnClickListener { pickZone(this) { id -> k.zone = id; infoZ() } }
        val zb2 = Button(this); zb2.text = "Ora locale"; zb2.isAllCaps = false
        zb2.setOnClickListener { k.zone = ""; infoZ() }
        zr.addView(zb1, LinearLayout.LayoutParams(0, -2, 1f)); zr.addView(zb2, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(zr)
        spin(box, "Nome della città", listOf("Mostra accanto alla data", "Non mostrare"), if (k.zlbl) 0 else 1) { k.zlbl = it == 0 }
        if (!digital && !Wg.analogTzOk()) {
            val nt = TextView(this)
            nt.text = "Su questo telefono l'orologio analogico non può usare un altro fuso: la scelta vale solo per il digitale."
            nt.setTextColor(MUTE); nt.textSize = 13f; nt.setPadding(0, dp(8), 0, 0)
            box.addView(nt)
        }

        title(box, if (digital) "Carattere di ora e data" else "Carattere della data")
        spin(box, "Carattere", Wg.FONT_LBL, k.font, Wg.FONT_FAM.map { Typeface.create(it, Typeface.NORMAL) }) { k.font = it }

        if (!digital) {
            title(box, "Quadrante e lancette")
            dialPrev = ImageView(this)
            dialPrev.setPadding(dp(8), dp(8), dp(8), dp(8))
            dialPrev.background = GradientDrawable().apply { setColor(0xFF1B222B.toInt()); cornerRadius = dp(16).toFloat() }
            label(box, "Quadrante")
            dialSpin = Spinner(this)
            dialSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, i: Long) {
                    if (pos in dialList.indices) { k.dial = dialList[pos].id; updPrev() }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            box.addView(dialSpin)
            fillDials()
            spin(box, "Colore delle lancette", Wg.HAND_LBL, k.hand) { k.hand = it; updPrev() }
            box.addView(dialPrev, LinearLayout.LayoutParams(dp(170), dp(170)).apply { topMargin = dp(12); gravity = android.view.Gravity.CENTER_HORIZONTAL })
            val nb = Button(this)
            nb.text = "Crea o modifica quadrante…"; nb.isAllCaps = false
            nb.setOnClickListener {
                startActivityForResult(Intent(this, DialBuilder::class.java).putExtra("dial", k.dial).putExtra("hand", k.hand), 5)
            }
            box.addView(nb)
        }

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
