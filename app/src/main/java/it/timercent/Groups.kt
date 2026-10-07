package it.timercent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Gruppo di sveglie. "on" è lo stato dell'interruttore del gruppo: quando lo si tocca viene applicato a tutte le
// sveglie del gruppo; dopo, ogni sveglia si accende e si spegne da sola senza toccare il gruppo.
// Una sveglia può stare in un solo gruppo. Le sveglie eliminate escono dal gruppo al caricamento.
class Gr(val id: String, var name: String, var on: Boolean, val ids: MutableList<String>)

object Groups {
    fun load(c: Context): MutableList<Gr> {
        val a = JSONArray(Alarms.p(c).getString("gr", "[]"))
        val alive = Alarms.load(c).map { it.id }.toSet()
        return MutableList(a.length()) { n ->
            val o = a.getJSONObject(n)
            val arr = o.optJSONArray("a") ?: JSONArray()
            val ids = ArrayList<String>()
            for (k in 0 until arr.length()) arr.getString(k).let { id -> if (id in alive) ids.add(id) }
            Gr(o.getString("id"), o.optString("n"), o.optBoolean("on", true), ids)
        }
    }

    fun save(c: Context, l: List<Gr>) {
        val a = JSONArray()
        l.forEach { g ->
            a.put(JSONObject().put("id", g.id).put("n", g.name).put("on", g.on).put("a", JSONArray(g.ids)))
        }
        Alarms.p(c).edit().putString("gr", a.toString()).apply()
    }

    fun put(c: Context, g: Gr) {
        val l = load(c)
        val i = l.indexOfFirst { it.id == g.id }
        if (i >= 0) l[i] = g else l.add(g)
        save(c, l)
    }

    fun remove(c: Context, id: String) { save(c, load(c).filter { it.id != id }) }

    // Gruppo a cui appartiene la sveglia, se c'è
    fun of(c: Context, alarmId: String): Gr? = load(c).firstOrNull { alarmId in it.ids }
}
