package it.timercent

// Risorse dell'analogico: 12 colori per lancetta, un layout per carattere.
// Ogni layout contiene le lancette di tutti i colori (12 per le ore, 12 per i minuti):
// il codice mostra solo la coppia scelta e nasconde le altre.
object AnaRes {
    const val COLORS = 12
    val HOUR = intArrayOf(R.drawable.clock_hour_h1, R.drawable.clock_hour_h2, R.drawable.clock_hour_h3, R.drawable.clock_hour_h4, R.drawable.clock_hour_h5, R.drawable.clock_hour_h6, R.drawable.clock_hour_h7, R.drawable.clock_hour_h8, R.drawable.clock_hour_h9, R.drawable.clock_hour_h10, R.drawable.clock_hour_h11, R.drawable.clock_hour_h12)
    val MIN = intArrayOf(R.drawable.clock_minute_h1, R.drawable.clock_minute_h2, R.drawable.clock_minute_h3, R.drawable.clock_minute_h4, R.drawable.clock_minute_h5, R.drawable.clock_minute_h6, R.drawable.clock_minute_h7, R.drawable.clock_minute_h8, R.drawable.clock_minute_h9, R.drawable.clock_minute_h10, R.drawable.clock_minute_h11, R.drawable.clock_minute_h12)
    val HOUR_ID = intArrayOf(R.id.ah1, R.id.ah2, R.id.ah3, R.id.ah4, R.id.ah5, R.id.ah6, R.id.ah7, R.id.ah8, R.id.ah9, R.id.ah10, R.id.ah11, R.id.ah12)
    val MIN_ID = intArrayOf(R.id.am1, R.id.am2, R.id.am3, R.id.am4, R.id.am5, R.id.am6, R.id.am7, R.id.am8, R.id.am9, R.id.am10, R.id.am11, R.id.am12)
    val LAYOUTS = intArrayOf(
        R.layout.widget_analog_f0, R.layout.widget_analog_f1, R.layout.widget_analog_f2, R.layout.widget_analog_f3,
        R.layout.widget_analog_f4, R.layout.widget_analog_f5, R.layout.widget_analog_f6, R.layout.widget_analog_f7
    )
}
