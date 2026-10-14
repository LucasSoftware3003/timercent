package it.timercent

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.File

// Backup di Google: i dati vengono salvati solo se l'utente lo ha attivato nel menu ⋮ → Opzioni.
class BackupGate : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor?) {
        // Nessun dato chiave-valore: il backup completo passa da onFullBackup
    }

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        // Nessun dato chiave-valore da ripristinare
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (!getSharedPreferences("ct", 0).getBoolean("backup", false)) return
        // Dati esplicitamente inclusi: preferenze (sveglie, timer, impostazioni) e cartella dei quadranti (font e foto)
        val prefs = File(applicationInfo.dataDir, "shared_prefs/ct.xml")
        if (prefs.exists()) fullBackupFile(prefs, data)
        File(filesDir, "dials").listFiles()?.forEach { if (it.isFile) fullBackupFile(it, data) }
    }
}
