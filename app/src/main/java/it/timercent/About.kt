package it.timercent

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

// Dati mostrati nella schermata «Informazioni e donazioni». Modifica solo questi tre.
object Info {
    const val AUTHOR = "Luca Serenelli aka Lucas3003"     // nome mostrato come autore
    const val PAYPAL = "LucasSoftware3003@gmail.com"         // link PayPal.Me (es. https://paypal.me/tuonome) oppure email PayPal; vuoto = niente donazioni
    const val SITE = "https://github.com/LucasSoftware3003/timercent"

    // Indirizzo da aprire per la donazione (vuoto se non impostato)
    fun payUrl(): String {
        val p = PAYPAL.trim()
        return when {
            p.isEmpty() -> ""
            p.startsWith("http") -> p
            "@" in p -> "https://www.paypal.com/donate/?business=" + Uri.encode(p) + "&currency_code=EUR"
            else -> "https://paypal.me/" + p.removePrefix("paypal.me/")
        }
    }
}

@Suppress("DEPRECATION")
fun MainActivity.aboutDlg() {
    val ver = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        "Versione " + pi.versionName + " (build " + code + ")"
    } catch (e: Exception) { "" }

    fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) { Toast.makeText(this, "Nessuna app per aprire il link", Toast.LENGTH_LONG).show() }
    }

    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(dp(22), dp(10), dp(22), 0)
    c.addView(tvw("Timercent", 24f, ACC))
    if (ver.isNotEmpty()) c.addView(tvw(ver, 13f, MUTE))
    c.addView(tvw("Timer, cronometro con decimi e centesimi, orologio internazionale, sveglie e widget personalizzabili. " +
        "Nessuna pubblicità, nessun account: i dati restano sul telefono.", 15f, FG).apply { setPadding(0, dp(14), 0, 0) })
    c.addView(tvw("Creata da " + Info.AUTHOR, 15f, FG).apply { setPadding(0, dp(14), 0, 0) })

    val pay = Info.payUrl()
    if (pay.isNotEmpty()) {
        c.addView(tvw("Se l'app ti è utile puoi offrirmi un caffè: la donazione è libera e facoltativa.", 14f, MUTE)
            .apply { setPadding(0, dp(14), 0, dp(4)) })
        c.addView(btn("Dona con PayPal", ACC, DARK) { open(pay) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(6) })
        c.addView(btn("Copia l'indirizzo PayPal", CARD, FG) {
            val t = Info.PAYPAL.trim()
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PayPal", t))
            Toast.makeText(this, "Copiato", Toast.LENGTH_SHORT).show()
        }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8) })
    }
    c.addView(btn("Codice sorgente e aggiornamenti", CARD, FG) { open(Info.SITE) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8); bottomMargin = dp(8) })

    AlertDialog.Builder(this).setTitle("Informazioni").setView(ScrollView(this).apply { addView(c) })
        .setNegativeButton("Chiudi", null).show()
}
