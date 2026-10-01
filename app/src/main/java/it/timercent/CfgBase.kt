package it.timercent

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

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
        fun paint() {
            views.forEachIndexed { i, v ->
                val chosen = pal[i] == sel
                val edge = if (Color.luminance(pal[i]) > 0.5f) Color.BLACK else Color.WHITE
                (v.background as GradientDrawable).setStroke(dp(if (chosen) 3 else 1), if (chosen) edge else 0x55FFFFFF)
            }
        }
        pal.forEachIndexed { i, col ->
            val v = View(this)
            v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }
            v.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) }
            v.setOnClickListener { sel = pal[i]; paint(); on(sel) }
            views.add(v); row.addView(v)
        }
        paint()
        box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }
}
