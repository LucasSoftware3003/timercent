package it.timercent

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

// Funzioni comuni alle schermate di configurazione (widget e costruttore di quadranti)
open class CfgBase : Activity() {
    protected val FG = Color.parseColor("#EEF0F3")
    protected val MUTE = Color.parseColor("#8A93A0")
    protected val ACC = Color.parseColor("#FFB020")
    protected fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    protected fun title(box: LinearLayout, t: String) {
        box.addView(TextView(this).apply {
            text = t; setTextColor(ACC); textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(2))
        })
    }

    protected fun label(box: LinearLayout, t: String) {
        box.addView(TextView(this).apply { text = t; setTextColor(MUTE); textSize = 13f; setPadding(0, dp(10), 0, 0) })
    }

    // Riga di spiegazione sotto un comando
    protected fun note(box: LinearLayout, t: String) {
        box.addView(TextView(this).apply { text = t; setTextColor(MUTE); textSize = 12f; setPadding(0, dp(4), 0, dp(2)) })
    }

    protected fun spin(box: LinearLayout, lbl: String, items: List<String>, cur: Int, faces: List<Typeface>? = null, on: (Int) -> Unit): Spinner {
        label(box, lbl)
        val s = Spinner(this)
        s.adapter = object : ArrayAdapter<String>(this@CfgBase, android.R.layout.simple_spinner_dropdown_item, items) {
            private fun face(v: View, p: Int) { if (faces != null) (v as? TextView)?.typeface = faces[p] }
            override fun getView(p: Int, v: View?, g: ViewGroup): View = super.getView(p, v, g).also { face(it, p) }
            override fun getDropDownView(p: Int, v: View?, g: ViewGroup): View = super.getDropDownView(p, v, g).also { face(it, p) }
        }
        s.setSelection(cur)
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, i: Long) { on(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        box.addView(s)
        return s
    }

    protected fun seek(box: LinearLayout, lbl: String, lo: Int, hi: Int, cur: Int, unit: String = " sp", on: (Int) -> Unit) {
        val t = TextView(this).apply { setTextColor(MUTE); textSize = 13f; setPadding(0, dp(10), 0, 0) }
        t.text = "$lbl: $cur$unit"
        val s = SeekBar(this)
        s.max = hi - lo
        s.progress = cur - lo
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { t.text = "$lbl: ${p + lo}$unit"; on(p + lo) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(t); box.addView(s)
    }

    protected fun colors(box: LinearLayout, lbl: String, cur: Int, pal: IntArray = Wg.PAL, on: (Int) -> Unit) {
        label(box, lbl)
        val row = LinearLayout(this).apply { setPadding(0, dp(6), 0, dp(2)) }
        val views = ArrayList<View>()
        var sel = cur
        var custom = if (pal.contains(cur)) 0 else cur   // colore esatto scelto con «#» (0 = nessuno)
        val hex = TextView(this).apply {
            text = "#"; textSize = 16f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) }
        }
        fun paint() {
            views.forEachIndexed { i, v ->
                val chosen = pal[i] == sel
                val edge = if (Color.luminance(pal[i]) > 0.5f) Color.BLACK else Color.WHITE
                (v.background as GradientDrawable).setStroke(dp(if (chosen) 3 else 1), if (chosen) edge else 0x55FFFFFF)
            }
            val g = hex.background as GradientDrawable
            val light = custom != 0 && Color.luminance(custom) > 0.5f
            g.setColor(if (custom != 0) custom else 0xFF2A323D.toInt())
            hex.setTextColor(if (custom == 0) FG else if (light) Color.BLACK else Color.WHITE)
            val chosen = custom != 0 && custom == sel
            g.setStroke(dp(if (chosen) 3 else 1), if (chosen) (if (light) Color.BLACK else Color.WHITE) else 0x55FFFFFF)
        }
        pal.forEachIndexed { i, col ->
            val v = View(this)
            v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }
            v.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) }
            v.setOnClickListener { sel = pal[i]; paint(); on(sel) }
            views.add(v); row.addView(v)
        }
        hex.setOnClickListener {
            val et = EditText(this)
            et.setSingleLine(); et.hint = "#RRGGBB"
            et.filters = arrayOf(android.text.InputFilter.LengthFilter(7))
            et.setText(String.format(java.util.Locale.ROOT, "#%06X", (if (custom != 0) custom else sel) and 0xFFFFFF))
            et.setSelection(et.text.length)
            val fl = FrameLayout(this); fl.setPadding(dp(20), dp(8), dp(20), 0); fl.addView(et)
            AlertDialog.Builder(this).setTitle("Colore esatto")
                .setMessage("Scrivi il codice esadecimale, per esempio #FFB020")
                .setView(fl)
                .setPositiveButton("OK") { _, _ ->
                    val t = et.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                    val c = try { Color.parseColor(t) } catch (e: Exception) { null }
                    if (c == null || t.length != 7) Toast.makeText(this, "Codice non valido", Toast.LENGTH_LONG).show()
                    else { custom = c or 0xFF000000.toInt(); sel = custom; paint(); on(sel) }
                }.setNegativeButton("Annulla", null).show()
        }
        row.addView(hex)
        paint()
        box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }
}
