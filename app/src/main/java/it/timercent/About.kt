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
    const val PAYPAL_ME = "https://paypal.me/LucasSoftware3003"
    // Indirizzo PayPal mostrato e copiabile: chi vuole può inviare dall'app PayPal. Vuoto = non mostrarlo.
    const val PAYPAL_MAIL = "LucasSoftware3003@gmail.com"
    const val SITE = "https://github.com/LucasSoftware3003/timercent"
    const val RELEASES = SITE + "/releases"   // pagina con tutte le versioni scaricabili

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
        getString(R.string.version_fmt, pi.versionName, code.toString())
    } catch (e: Exception) { "" }

    // Apre il link nel browser predefinito (non nell'app PayPal o in altre app che lo intercettano)
    fun open(url: String) {
        try {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
            i.data = Uri.parse(url)
            startActivity(i)
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (e2: Exception) { Toast.makeText(this, getString(R.string.about_no_browser), Toast.LENGTH_LONG).show() }
        }
    }

    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(dp(22), dp(10), dp(22), 0)
    c.addView(tvw("Timercent", 24f, ACC))
    if (ver.isNotEmpty()) c.addView(tvw(ver, 13f, MUTE))
    c.addView(tvw(getString(R.string.about_desc_1) +
        getString(R.string.about_desc_2), 15f, FG).apply { setPadding(0, dp(14), 0, 0) })
    c.addView(tvw(getString(R.string.about_created_by, Info.AUTHOR), 15f, FG).apply { setPadding(0, dp(14), 0, 0) })
    c.addView(tvw(getString(R.string.about_license), 13f, MUTE).apply { setPadding(0, dp(8), 0, 0) })

    val pay = Info.payUrl()
    val mail = Info.PAYPAL_MAIL.trim()
    if (pay.isNotEmpty() || mail.isNotEmpty()) {
        c.addView(tvw(getString(R.string.donate_text) +
            (if (pay.isEmpty()) getString(R.string.donate_mail_fmt, mail) else ""), 14f, MUTE)
            .apply { setPadding(0, dp(14), 0, dp(4)) })
        if (pay.isNotEmpty()) c.addView(btn(getString(R.string.donate_btn), ACC, DARK) { open(pay) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(6) })
        if (mail.isNotEmpty()) c.addView(btn(getString(R.string.copy_paypal), if (pay.isEmpty()) ACC else CARD, if (pay.isEmpty()) DARK else FG) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PayPal", mail))
            Toast.makeText(this, getString(R.string.copied_fmt, mail), Toast.LENGTH_SHORT).show()
        }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8) })
    }
    c.addView(btn(getString(R.string.check_updates), CARD, FG) { open(Info.RELEASES) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8) })
    c.addView(btn(getString(R.string.source_code), CARD, FG) { open(Info.SITE) }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8) })
    c.addView(btn(getString(R.string.license_btn), CARD, FG) { open(Info.SITE + "/blob/main/LICENSE") }, lp(-1, -2, 0f, 0).apply { topMargin = dp(8); bottomMargin = dp(8) })

    AlertDialog.Builder(this).setTitle(getString(R.string.about_title)).setView(ScrollView(this).apply { addView(c) })
        .setNegativeButton(getString(R.string.close), null).show()
}
