package it.timercent

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// Costruttore di quadranti analogici: anteprima in alto, controlli sotto
class DialBuilder : CfgBase() {
    private lateinit var orig: DialSpec
    private lateinit var s: DialSpec
    private lateinit var prev: ImageView
    private lateinit var nameEt: EditText
    private lateinit var imgInfo: TextView
    private lateinit var fontInfo: TextView
    private var hand = 0

    private fun upd() { prev.setImageBitmap(Dials.preview(this, s, hand)) }

    private fun info() {
        imgInfo.text = if (s.img != null) "Immagine di sfondo: impostata" else "Immagine di sfondo: nessuna"
        fontInfo.text = if (s.fontFile != null) "Carattere: file importato" else "Carattere: di sistema"
    }

    private fun toast(t: String) { Toast.makeText(this, t, Toast.LENGTH_LONG).show() }

    private fun mkBtn(t: String, f: () -> Unit): Button {
        val b = Button(this)
        b.text = t; b.isAllCaps = false
        b.setOnClickListener { f() }
        return b
    }

    private fun pair(box: LinearLayout, a: Button, b: Button) {
        val r = LinearLayout(this)
        r.addView(a, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(r)
    }

    private fun pick(type: String, rq: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), rq)
    }

    private fun save(asNew: Boolean) {
        s.name = nameEt.text.toString().trim().ifEmpty { "Quadrante" }
        val isUser = !orig.id.startsWith("p")
        s.id = if (isUser && !asNew) orig.id else "u" + System.currentTimeMillis()
        Dials.putUser(this, s)
        Dials.refreshWidgets(this)
        setResult(RESULT_OK, Intent().putExtra("dial", s.id))
        finish()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setResult(RESULT_CANCELED)
        val has = intent.hasExtra("dial")
        orig = Dials.find(this, intent.getStringExtra("dial") ?: "p0")
        s = orig.copy()
        if (orig.id.startsWith("p")) s.name = if (has) orig.name + " (mio)" else "Mio quadrante"
        hand = intent.getIntExtra("hand", 0).coerceIn(0, Wg.HAND_LBL.size - 1)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        prev = ImageView(this)
        prev.setPadding(dp(8), dp(8), dp(8), dp(8))
        prev.background = GradientDrawable().apply { setColor(0xFF1B222B.toInt()); cornerRadius = dp(16).toFloat() }
        root.addView(prev, LinearLayout.LayoutParams(-1, dp(210)).apply { setMargins(dp(20), dp(12), dp(20), dp(4)) })

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(4), dp(20), dp(28))

        title(box, "Nome")
        nameEt = EditText(this)
        nameEt.setText(s.name); nameEt.setSingleLine(); nameEt.setTextColor(FG)
        box.addView(nameEt)

        title(box, "Forma e sfondo")
        spin(box, "Forma", Dials.SHAPE_LBL, s.shape) { s.shape = it; upd() }
        colors(box, "Colore dello sfondo", s.bgCol, Wg.DPAL) { s.bgCol = it; upd() }
        spin(box, "Trasparenza dello sfondo", Dials.OP_LBL, s.bgOp) { s.bgOp = it; upd() }
        imgInfo = TextView(this)
        imgInfo.setTextColor(MUTE); imgInfo.textSize = 13f; imgInfo.setPadding(0, dp(12), 0, 0)
        box.addView(imgInfo)
        pair(box, mkBtn("Scegli immagine…") { pick("image/*", 11) }, mkBtn("Rimuovi immagine") { s.img = null; info(); upd() })
        note(box, "Questa immagine resta dentro il quadrante e viene ritagliata a quadrato. Per una foto che copre tutto il widget usa «Scegli foto» nella configurazione del widget.")
        seek(box, "Spessore del bordo", 0, 12, s.bdW, "") { s.bdW = it; upd() }
        colors(box, "Colore del bordo", s.bdCol, Wg.DPAL) { s.bdCol = it; upd() }

        title(box, "Numeri")
        spin(box, "Tipo di numeri", Dials.NUM_LBL, s.num) { s.num = it; upd() }
        fontInfo = TextView(this)
        fontInfo.setTextColor(MUTE); fontInfo.textSize = 13f; fontInfo.setPadding(0, dp(12), 0, 0)
        spin(box, "Carattere", Wg.FONT_LBL, s.font, Wg.FONT_FAM.map { Typeface.create(it, Typeface.NORMAL) }) {
            if (it != s.font) { s.font = it; s.fontFile = null; info() }
            upd()
        }
        box.addView(fontInfo)
        pair(box, mkBtn("Importa carattere .ttf…") { pick("*/*", 12) }, mkBtn("Usa di sistema") { s.fontFile = null; info(); upd() })
        seek(box, "Dimensione dei numeri", 6, 30, s.numSize, " %") { s.numSize = it; upd() }
        colors(box, "Colore dei numeri", s.numCol, Wg.DPAL) { s.numCol = it; upd() }

        title(box, "Tacche")
        spin(box, "Stile", Dials.TICK_LBL, s.tick) { s.tick = it; upd() }
        seek(box, "Lunghezza", 4, 24, s.tickLen, " %") { s.tickLen = it; upd() }
        seek(box, "Spessore", 4, 24, s.tickW, "") { s.tickW = it; upd() }
        colors(box, "Colore delle tacche", s.tickCol, Wg.DPAL) { s.tickCol = it; upd() }

        title(box, "Anteprima")
        spin(box, "Colore delle lancette (solo per vedere l'effetto)", Wg.HAND_LBL, hand) { hand = it; upd() }

        title(box, "Salvataggio")
        box.addView(mkBtn("Salva") { save(false) })
        if (!orig.id.startsWith("p")) {
            box.addView(mkBtn("Salva come nuovo") { save(true) })
            box.addView(mkBtn("Elimina quadrante") {
                AlertDialog.Builder(this).setTitle("Eliminare \"" + orig.name + "\"?")
                    .setMessage("I widget che lo usano torneranno al quadrante Classico.")
                    .setPositiveButton("Elimina") { _, _ ->
                        Dials.removeUser(this, orig.id)
                        Dials.refreshWidgets(this)
                        setResult(RESULT_OK, Intent().putExtra("dial", "p0"))
                        finish()
                    }.setNegativeButton("Annulla", null).show()
            })
        }

        info()
        upd()
        val sv = ScrollView(this)
        sv.addView(box)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        val u = d?.data
        if (rs != RESULT_OK || u == null) return
        if (rq == 11) {
            val n = Dials.importImage(this, u)
            if (n != null) { s.img = n; info(); upd() } else toast("Immagine non valida")
        } else if (rq == 12) {
            val n = Dials.importFont(this, u)
            if (n != null) { s.fontFile = n; info(); upd() } else toast("File di caratteri non valido")
        }
    }
}

// Voce del menu ⋮: elenco dei quadranti da modificare, oppure uno nuovo
fun MainActivity.dialMenu() {
    val l = Dials.all(this)
    val names = arrayOf("+ Nuovo quadrante") + l.map { it.name }
    AlertDialog.Builder(this).setTitle("Quadranti analogici").setItems(names) { _, w ->
        val i = Intent(this, DialBuilder::class.java)
        if (w > 0) i.putExtra("dial", l[w - 1].id)
        startActivity(i)
    }.setNegativeButton("Chiudi", null).show()
}
