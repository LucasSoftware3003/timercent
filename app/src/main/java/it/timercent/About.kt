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

// Dati mostrati nella schermata «Informazioni e donazioni». Modifica solo questi.
object Info {
    const val AUTHOR = "Luca Serenelli aka Lucas3003"
    // Link PayPal.Me (es. https://paypal.me/tuonome): funziona anche con un conto personale. Vuoto = niente tasto «Dona».
    const val PAYPAL_ME = ""
    // Indirizzo PayPal mostrato e copiabile: chi vuole può inviare dall'app PayPal. Vuoto = non mostrarlo.
    const val PAYPAL_MAIL = "LucasSoftware3003@gmail.com"
    const val SITE = "https://github.com/LucasSoftware3003/timercent"

    fun payUrl(): String {
        val p = PAYPAL_ME.trim()
        return when {
            p.isEmpty() -> ""
            p.startsWith("http") -> p
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

    // Apre il link nel browser predefinito (non nell'app PayPal o in altre app che lo intercettano)
    fun open(url: String) {
        try {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
            i.data = Uri.parse(url)
            startActivity(i)
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (e2: Exception) { Toast.makeText(this, "Nessun browser per aprire il link", Toast.LENGTH_LONG).show() }
        }
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
    val mail = Info.PAYPAL_MAIL.trim()
    if (pay.isNotEmpty() || mail.isNotEmpty()) {
        c.addView(tvw("Se l'app ti è utile puoi offrirmi un caffè: la donazione è libera e facoltativa." +
            (if (pay.isEmpty()) " Dall'app PayPal puoi inviare a questo indirizzo:\n$mail" else ""), 14f, MUTE)
            .apply { setPadding(0, dp(14), 0, dp(4)) })
        if (pay.isNotEmpty()) c.addView(btn("Sostieni lo sviluppo", ACC, DARK) { open(pay) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(6) })
        if (mail.isNotEmpty()) c.addView(btn("Copia l'indirizzo PayPal", if (pay.isEmpty()) ACC else CARD, if (pay.isEmpty()) DARK else FG) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PayPal", mail))
            Toast.makeText(this, "Copiato: $mail", Toast.LENGTH_SHORT).show()
        }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8) })
    }
    c.addView(btn("Codice sorgente e aggiornamenti", CARD, FG) { open(Info.SITE) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8); bottomMargin = dp(8) })

    AlertDialog.Builder(this).setTitle("Informazioni").setView(ScrollView(this).apply { addView(c) })
        .setNegativeButton("Chiudi", null).show()
}
