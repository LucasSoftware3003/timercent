package it.timercent

import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import java.time.Instant
import java.time.ZoneId

// Nome della città (in italiano) e differenza di fuso, come nella scheda Orologio
fun zoneCity(id: String): String {
    val n: String? = try { android.icu.text.TimeZoneNames.getInstance(java.util.Locale.getDefault()).getExemplarLocationName(id) } catch (e: Exception) { null }
    return if (!n.isNullOrBlank()) n else id.substringAfterLast('/').replace('_', ' ')
}

fun zoneGmt(id: String): String {
    val z = try { ZoneId.of(id) } catch (e: Exception) { return "" }
    val m = z.rules.getOffset(Instant.now()).totalSeconds / 60
    val a = Math.abs(m)
    return "GMT" + (if (m < 0) "-" else "+") + (a / 60) + (if (a % 60 != 0) ":%02d".format(a % 60) else "")
}

// Finestra di ricerca città; restituisce l'id del fuso (es. America/New_York)
fun pickZone(a: Activity, onPick: (String) -> Unit) {
    val den = a.resources.displayMetrics.density
    fun dp(x: Int) = (x * den).toInt()
    val pre = listOf("Africa/", "America/", "Antarctica/", "Arctic/", "Asia/", "Atlantic/", "Australia/", "Europe/", "Indian/", "Pacific/")
    val all = ZoneId.getAvailableZoneIds().filter { id -> pre.any { id.startsWith(it) } }
        .map { id -> Triple(zoneCity(id), id, zoneGmt(id)) }
        .distinctBy { it.first + it.third }.sortedBy { it.first.lowercase() }
    var shown = all
    val ad = ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, ArrayList<String>())
    fun filt(q: String) {
        val k = q.trim().lowercase()
        shown = if (k.isEmpty()) all else all.filter { it.first.lowercase().contains(k) || it.second.lowercase().replace('_', ' ').contains(k) }
        ad.clear(); ad.addAll(shown.map { "${it.first}  (${it.third})" }); ad.notifyDataSetChanged()
    }
    filt("")
    val et = EditText(a)
    et.hint = "Cerca città"; et.setSingleLine()
    et.addTextChangedListener(object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { filt(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, x: Int, y: Int, z: Int) {}
        override fun onTextChanged(s: CharSequence?, x: Int, y: Int, z: Int) {}
    })
    val lv = ListView(a)
    lv.adapter = ad
    val c = LinearLayout(a)
    c.orientation = LinearLayout.VERTICAL; c.setPadding(dp(16), dp(8), dp(16), 0)
    c.addView(et); c.addView(lv, LinearLayout.LayoutParams(-1, dp(320)))
    var dlg: AlertDialog? = null
    lv.setOnItemClickListener { _, _, pos, _ -> onPick(shown[pos].second); dlg?.dismiss() }
    dlg = AlertDialog.Builder(a).setTitle("Scegli la città").setView(c).setNegativeButton("Annulla", null).show()
}
