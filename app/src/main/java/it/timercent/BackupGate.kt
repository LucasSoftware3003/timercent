package it.timercent

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor

// Backup di Google: i dati vengono salvati solo se l'utente lo ha attivato in «Informazioni e donazioni».
class BackupGate : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor?) {
        // Nessun dato chiave-valore: il backup completo passa da onFullBackup
    }

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        // Nessun dato chiave-valore da ripristinare
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (!getSharedPreferences("ct", 0).getBoolean("backup", false)) return
        super.onFullBackup(data)
    }
}
