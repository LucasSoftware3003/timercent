package it.timercent

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// Descrizione di un quadrante analogico. I colori sono ARGB opachi.
class DialSpec(
    var id: String = "u0",
    var name: String = "Quadrante",
    var shape: Int = 0,                      // 0 tonda, 1 quadrata arrotondata
    var bgCol: Int = 0xFF14181F.toInt(),
    var bgOp: Int = 0,                       // indice in Dials.ALPHAS
    var bdW: Int = 0,                        // spessore bordo 0..12
    var bdCol: Int = 0xFF8A93A0.toInt(),
    var img: String? = null,                 // immagine di sfondo importata (nome file)
    var num: Int = 0,                        // 0 nessuno, 1 arabi 1-12, 2 arabi 12-3-6-9, 3 romani
    var font: Int = 0,                       // indice in Wg.FONT_FAM
    var fontFile: String? = null,            // .ttf importato (ha la precedenza)
    var numSize: Int = 16,                   // % del raggio
    var numCol: Int = 0xFFEEF0F3.toInt(),
    var tick: Int = 1,                       // 0 nessuna, 1 ore, 2 ore e minuti, 3 punti, 4 solo quarti
    var tickLen: Int = 16,                   // % del raggio
    var tickW: Int = 13,                     // spessore (0,2% del diametro)
    var tickCol: Int = 0xFFEEF0F3.toInt()
) {
    fun toJson(): JSONObject {
        val o = JSONObject().put("id", id).put("name", name).put("shape", shape).put("bgCol", bgCol).put("bgOp", bgOp)
            .put("bdW", bdW).put("bdCol", bdCol).put("num", num).put("font", font).put("numSize", numSize)
            .put("numCol", numCol).put("tick", tick).put("tickLen", tickLen).put("tickW", tickW).put("tickCol", tickCol)
        if (img != null) o.put("img", img)
        if (fontFile != null) o.put("fontFile", fontFile)
        return o
    }

    fun copy(): DialSpec = fromJson(toJson())

    companion object {
        fun fromJson(o: JSONObject): DialSpec {
            val d = DialSpec()
            return DialSpec(
                o.getString("id"), o.optString("name", d.name), o.optInt("shape", d.shape), o.optInt("bgCol", d.bgCol),
                o.optInt("bgOp", d.bgOp), o.optInt("bdW", d.bdW), o.optInt("bdCol", d.bdCol),
                if (o.has("img")) o.getString("img") else null,
                o.optInt("num", d.num), o.optInt("font", d.font),
                if (o.has("fontFile")) o.getString("fontFile") else null,
                o.optInt("numSize", d.numSize), o.optInt("numCol", d.numCol), o.optInt("tick", d.tick),
                o.optInt("tickLen", d.tickLen), o.optInt("tickW", d.tickW), o.optInt("tickCol", d.tickCol)
            )
        }
    }
}

object Dials {
    const val SIZE = 480
    val ALPHAS = intArrayOf(255, 190, 110, 0)
    val OP_LBL = listOf("Opaco", "Poco trasparente", "Molto trasparente", "Nessuno sfondo")
    val SHAPE_LBL = listOf("Tonda", "Quadrata arrotondata")
    val NUM_LBL = listOf("Nessuno", "Arabi 1-12", "Arabi 12-3-6-9", "Romani I-XII")
    val TICK_LBL = listOf("Nessuna", "Ore (linee)", "Ore e minuti (linee)", "Ore (punti)", "Solo quarti (linee)")
    private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

    private val DARK = 0xFF222222.toInt()

    // Il numero 0 riproduce esattamente il quadrante delle versioni precedenti
    val PRESETS: List<DialSpec> = listOf(
        DialSpec(id = "p0", name = "Classico", bgOp = 3),
        DialSpec(id = "p1", name = "Solo quarti", bgOp = 3, tick = 4, tickLen = 14, tickW = 11),
        DialSpec(id = "p2", name = "Numeri arabi", bgCol = 0xFF14181F.toInt(), bdW = 2, num = 1, numSize = 15, tick = 0),
        DialSpec(id = "p3", name = "Romano chiaro", bgCol = 0xFFF5F1E6.toInt(), bdW = 3, bdCol = DARK, num = 3, font = 5,
            numSize = 13, numCol = DARK, tick = 1, tickCol = DARK),
        DialSpec(id = "p4", name = "Quattro numeri", bgCol = 0xFF1B2430.toInt(), num = 2, font = 3, numSize = 22, tick = 2, tickLen = 10, tickW = 10),
        DialSpec(id = "p5", name = "Punti", bgOp = 3, tick = 3, tickW = 12),
        DialSpec(id = "p6", name = "Moderno chiaro", bgCol = 0xFFEEF0F3.toInt(), tick = 1, tickCol = 0xFF14181F.toInt())
    )

    fun dir(c: Context): File { val d = File(c.filesDir, "dials"); d.mkdirs(); return d }
    private fun prefs(c: Context) = c.getSharedPreferences("ct", 0)

    fun user(c: Context): MutableList<DialSpec> {
        val a = try { JSONArray(prefs(c).getString("dials", "[]")) } catch (e: Exception) { JSONArray() }
        val l = ArrayList<DialSpec>()
        for (i in 0 until a.length()) {
            try { l.add(DialSpec.fromJson(a.getJSONObject(i))) } catch (e: Exception) { }
        }
        return l
    }

    private fun saveUser(c: Context, l: List<DialSpec>) {
        val a = JSONArray()
        l.forEach { a.put(it.toJson()) }
        prefs(c).edit().putString("dials", a.toString()).apply()
    }

    fun putUser(c: Context, s: DialSpec) {
        val l = user(c)
        val i = l.indexOfFirst { it.id == s.id }
        if (i >= 0) l[i] = s else l.add(s)
        saveUser(c, l)
    }

    fun removeUser(c: Context, id: String) { saveUser(c, user(c).filter { it.id != id }) }

    fun all(c: Context): List<DialSpec> = PRESETS + user(c)

    fun find(c: Context, id: String): DialSpec = all(c).firstOrNull { it.id == id } ?: PRESETS[0]

    fun bitmap(c: Context, id: String): Bitmap = render(c, find(c, id))

    // Ridisegna tutti i widget analogici (dopo aver salvato o eliminato un quadrante)
    fun refreshWidgets(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        m.getAppWidgetIds(ComponentName(c, AnalogWidget::class.java)).forEach { Wg.refresh(c, it, false) }
    }

    fun face(c: Context, s: DialSpec): Typeface {
        val ff = s.fontFile
        if (ff != null) {
            try { return Typeface.createFromFile(File(dir(c), ff)) } catch (e: Exception) { }
        }
        return Typeface.create(Wg.FONT_FAM[s.font.coerceIn(0, Wg.FONT_FAM.size - 1)], Typeface.NORMAL)
    }

    private fun loadImg(c: Context, name: String): Bitmap? =
        try { BitmapFactory.decodeFile(File(dir(c), name).path) } catch (e: Exception) { null }

    fun render(c: Context, s: DialSpec): Bitmap {
        val n = SIZE
        val sz = n.toFloat()
        val f = sz / 2f
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val bd = s.bdW * sz * 0.004f
        val ins = bd / 2f + 2f
        val shape = Path()
        if (s.shape == 0) shape.addCircle(f, f, f - ins, Path.Direction.CW)
        else shape.addRoundRect(RectF(ins, ins, sz - ins, sz - ins), sz * 0.18f, sz * 0.18f, Path.Direction.CW)

        // Sfondo: immagine ritagliata sulla forma, oppure colore pieno
        val op = ALPHAS[s.bgOp.coerceIn(0, ALPHAS.size - 1)]
        val img = s.img?.let { loadImg(c, it) }
        if (img != null) {
            val layer = cv.saveLayer(0f, 0f, sz, sz, null)
            cv.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
            val pi = Paint(Paint.FILTER_BITMAP_FLAG)
            pi.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            cv.drawBitmap(img, null, RectF(0f, 0f, sz, sz), pi)
            cv.restoreToCount(layer)
        } else if (op > 0) {
            val pb = Paint(Paint.ANTI_ALIAS_FLAG)
            pb.color = (s.bgCol and 0xFFFFFF) or (op shl 24)
            cv.drawPath(shape, pb)
        }
        if (s.bdW > 0) {
            val pk = Paint(Paint.ANTI_ALIAS_FLAG)
            pk.style = Paint.Style.STROKE; pk.strokeWidth = bd; pk.color = s.bdCol
            cv.drawPath(shape, pk)
        }

        // Tacche
        val rOut = f * 0.90f
        val q = f * s.tickLen / 100f
        val w = sz * 0.002f * s.tickW
        val pc = Paint(Paint.ANTI_ALIAS_FLAG)
        pc.strokeCap = Paint.Cap.ROUND
        fun col(a: Float): Int = (s.tickCol and 0xFFFFFF) or ((255 * a).toInt() shl 24)
        fun line(deg: Int, len: Float, width: Float, color: Int) {
            val r = Math.toRadians(deg.toDouble())
            val sn = Math.sin(r).toFloat()
            val cs = Math.cos(r).toFloat()
            pc.style = Paint.Style.STROKE; pc.strokeWidth = width; pc.color = color
            cv.drawLine(f + sn * rOut, f - cs * rOut, f + sn * (rOut - len), f - cs * (rOut - len), pc)
        }
        var inner = rOut
        when (s.tick) {
            1, 2 -> {
                for (i in 0 until 12) {
                    val qt = i % 3 == 0
                    line(i * 30, if (qt) q else q * 0.45f, if (qt) w else w * 0.62f, if (qt) col(1f) else col(0.6f))
                }
                if (s.tick == 2) for (m in 0 until 60) if (m % 5 != 0) line(m * 6, q * 0.3f, w * 0.4f, col(0.45f))
                inner = rOut - q
            }
            4 -> {
                for (i in 0 until 4) line(i * 90, q, w, col(1f))
                inner = rOut - q
            }
            3 -> {
                val dr = w * 0.9f
                pc.style = Paint.Style.FILL; pc.color = s.tickCol
                for (i in 0 until 12) {
                    val r = Math.toRadians(i * 30.0)
                    val rr = if (i % 3 == 0) dr else dr * 0.65f
                    cv.drawCircle(f + (Math.sin(r) * (rOut - dr)).toFloat(), f - (Math.cos(r) * (rOut - dr)).toFloat(), rr, pc)
                }
                inner = rOut - dr * 2f
            }
        }

        // Numeri, posti subito dentro le tacche
        if (s.num != 0) {
            val ts = f * s.numSize / 100f
            val pn = Paint(Paint.ANTI_ALIAS_FLAG)
            pn.textSize = ts; pn.color = s.numCol; pn.textAlign = Paint.Align.CENTER; pn.typeface = face(c, s)
            val rn = inner - ts * 0.62f - f * 0.03f
            val dy = -(pn.ascent() + pn.descent()) / 2f
            for (h in 1..12) {
                if (s.num == 2 && h % 3 != 0) continue
                val r = Math.toRadians(h * 30.0)
                val txt = if (s.num == 3) ROMAN[h - 1] else h.toString()
                cv.drawText(txt, f + (Math.sin(r) * rn).toFloat(), f - (Math.cos(r) * rn).toFloat() + dy, pn)
            }
        }
        return bmp
    }

    // Anteprima: quadrante + lancette (le stesse risorse del widget) alle 10:10
    fun preview(c: Context, s: DialSpec, hand: Int): Bitmap {
        val b = render(c, s)
        val cv = Canvas(b)
        val sz = cv.width
        val h = hand.coerceIn(0, AnaRes.HANDS - 1)
        val list = listOf(Pair(AnaRes.HOUR[h], 305f), Pair(AnaRes.MIN[h], 60f))
        for (p in list) {
            val d = c.getDrawable(p.first) ?: continue
            cv.save()
            cv.rotate(p.second, sz / 2f, sz / 2f)
            d.setBounds(0, 0, sz, sz)
            d.draw(cv)
            cv.restore()
        }
        return b
    }

    // Immagine scelta dall'utente: ritaglio quadrato centrale, ridotto a SIZE
    fun importImage(c: Context, u: Uri): String? {
        try {
            val opt = BitmapFactory.Options()
            opt.inJustDecodeBounds = true
            c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opt) }
            var ss = 1
            while (opt.outWidth / ss > SIZE * 2 && opt.outHeight / ss > SIZE * 2) ss *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = ss
            val src = c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            val m = minOf(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - m) / 2, (src.height - m) / 2, m, m)
            val out = Bitmap.createScaledBitmap(sq, SIZE, SIZE, true)
            val name = "img_" + System.currentTimeMillis() + ".png"
            FileOutputStream(File(dir(c), name)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return name
        } catch (e: Exception) { return null }
    }

    // File di caratteri .ttf/.otf: si controlla l'intestazione prima di accettarlo
    fun importFont(c: Context, u: Uri): String? {
        val name = "font_" + System.currentTimeMillis() + ".ttf"
        val f = File(dir(c), name)
        try {
            c.contentResolver.openInputStream(u)?.use { i -> FileOutputStream(f).use { o -> i.copyTo(o) } } ?: return null
            val h = ByteArray(4)
            f.inputStream().use { it.read(h) }
            val tag = String(h, Charsets.ISO_8859_1)
            val ok = tag == "OTTO" || tag == "true" || tag == "ttcf" ||
                (h[0] == 0.toByte() && h[1] == 1.toByte() && h[2] == 0.toByte() && h[3] == 0.toByte())
            if (!ok) { f.delete(); return null }
            Typeface.createFromFile(f)
            return name
        } catch (e: Exception) { f.delete(); return null }
    }
}
