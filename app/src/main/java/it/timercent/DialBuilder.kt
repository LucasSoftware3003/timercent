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
    private var hh = 0
    private var hm = 3

    private fun upd() { prev.setImageBitmap(Dials.preview(this, s, hh, hm)) }

    private fun info() {
        imgInfo.text = if (s.img != null) getString(R.string.img_set) else getString(R.string.img_none)
        fontInfo.text = if (s.fontFile != null) getString(R.string.font_imported) else getString(R.string.font_system)
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
        s.name = nameEt.text.toString().trim().ifEmpty { getString(R.string.dial_default_name) }
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
        if (orig.id.startsWith("p")) s.name = if (has) getString(R.string.dial_copy_fmt, orig.name) else getString(R.string.dial_new_name)
        hh = intent.getIntExtra("hh", 0).coerceIn(0, Wg.HAND_LBL.size - 1)
        hm = intent.getIntExtra("hm", 3).coerceIn(0, Wg.HAND_LBL.size - 1)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        prev = ImageView(this)
        prev.setPadding(dp(8), dp(8), dp(8), dp(8))
        prev.background = GradientDrawable().apply { setColor(0xFF1B222B.toInt()); cornerRadius = dp(16).toFloat() }
        root.addView(prev, LinearLayout.LayoutParams(-1, dp(210)).apply { setMargins(dp(20), dp(12), dp(20), dp(4)) })

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(4), dp(20), dp(28))

        title(box, getString(R.string.sec_name))
        nameEt = EditText(this)
        nameEt.setText(s.name); nameEt.setSingleLine(); nameEt.setTextColor(FG)
        box.addView(nameEt)

        title(box, getString(R.string.sec_shape_bg))
        spin(box, getString(R.string.shape), resources.getStringArray(R.array.shape_opts).toList(), s.shape) { s.shape = it; upd() }
        colors(box, getString(R.string.bg_color), s.bgCol, Wg.DPAL) { s.bgCol = it; upd() }
        spin(box, getString(R.string.bg_transp), resources.getStringArray(R.array.op_opts).toList(), s.bgOp) { s.bgOp = it; upd() }
        imgInfo = TextView(this)
        imgInfo.setTextColor(MUTE); imgInfo.textSize = 13f; imgInfo.setPadding(0, dp(12), 0, 0)
        box.addView(imgInfo)
        pair(box, mkBtn(getString(R.string.choose_image)) { pick("image/*", 11) }, mkBtn(getString(R.string.remove_image)) { s.img = null; info(); upd() })
        note(box, getString(R.string.image_note))
        seek(box, getString(R.string.border_width), 0, 12, s.bdW, "") { s.bdW = it; upd() }
        colors(box, getString(R.string.border_color), s.bdCol, Wg.DPAL) { s.bdCol = it; upd() }

        title(box, getString(R.string.sec_numbers))
        spin(box, getString(R.string.number_type), resources.getStringArray(R.array.num_opts).toList(), s.num) { s.num = it; upd() }
        fontInfo = TextView(this)
        fontInfo.setTextColor(MUTE); fontInfo.textSize = 13f; fontInfo.setPadding(0, dp(12), 0, 0)
        spin(box, getString(R.string.font), resources.getStringArray(R.array.font_labels).toList(), s.font, Wg.FONT_FAM.map { Typeface.create(it, Typeface.NORMAL) }) {
            if (it != s.font) { s.font = it; s.fontFile = null; info() }
            upd()
        }
        box.addView(fontInfo)
        pair(box, mkBtn(getString(R.string.import_font)) { pick("*/*", 12) }, mkBtn(getString(R.string.use_system_font)) { s.fontFile = null; info(); upd() })
        seek(box, getString(R.string.number_size), 6, 30, s.numSize, " %") { s.numSize = it; upd() }
        colors(box, getString(R.string.number_color), s.numCol, Wg.DPAL) { s.numCol = it; upd() }

        title(box, getString(R.string.sec_ticks))
        spin(box, getString(R.string.tick_style), resources.getStringArray(R.array.tick_opts).toList(), s.tick) { s.tick = it; upd() }
        seek(box, getString(R.string.tick_length), 4, 24, s.tickLen, " %") { s.tickLen = it; upd() }
        seek(box, getString(R.string.tick_width), 4, 24, s.tickW, "") { s.tickW = it; upd() }
        colors(box, getString(R.string.tick_color), s.tickCol, Wg.DPAL) { s.tickCol = it; upd() }

        title(box, getString(R.string.sec_preview))
        spin(box, getString(R.string.preview_hour_hand), resources.getStringArray(R.array.hand_colors).toList(), hh) { hh = it; upd() }
        spin(box, getString(R.string.preview_min_hand), resources.getStringArray(R.array.hand_colors).toList(), hm) { hm = it; upd() }

        title(box, getString(R.string.sec_save))
        box.addView(mkBtn(getString(R.string.save)) { save(false) })
        if (!orig.id.startsWith("p")) {
            box.addView(mkBtn(getString(R.string.save_as_new)) { save(true) })
            box.addView(mkBtn(getString(R.string.delete_dial)) {
                AlertDialog.Builder(this).setTitle(getString(R.string.delete_dial_title, orig.name))
                    .setMessage(getString(R.string.delete_dial_msg))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        Dials.removeUser(this, orig.id)
                        Dials.refreshWidgets(this)
                        setResult(RESULT_OK, Intent().putExtra("dial", "p0"))
                        finish()
                    }.setNegativeButton(getString(R.string.cancel), null).show()
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
            if (n != null) { s.img = n; info(); upd() } else toast(getString(R.string.invalid_image))
        } else if (rq == 12) {
            val n = Dials.importFont(this, u)
            if (n != null) { s.fontFile = n; info(); upd() } else toast(getString(R.string.invalid_font))
        }
    }
}

// Voce del menu ⋮: elenco dei quadranti da modificare, oppure uno nuovo
fun MainActivity.dialMenu() {
    val l = Dials.all(this)
    val names = arrayOf(getString(R.string.new_dial_item)) + l.map { it.name }
    AlertDialog.Builder(this).setTitle(getString(R.string.menu_dials)).setItems(names) { _, w ->
        val i = Intent(this, DialBuilder::class.java)
        if (w > 0) i.putExtra("dial", l[w - 1].id)
        startActivity(i)
    }.setNegativeButton(getString(R.string.close), null).show()
}
