package de.spahr.ausgaben.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;


import de.spahr.ausgaben.R;
import de.spahr.ausgaben.net.smb.SmbDiagnostics;

/**
 * Führt die SMB-Diagnose aus und zeigt den Bericht – gemeinsam genutzt von Einstellungen und
 * Erststart-Assistent, weil die Frage „warum geht es nicht?" an beiden Stellen dieselbe ist (beim
 * Erststart sogar die wichtigere).
 *
 * <p>Der Bericht enthält kein Passwort und keinen Benutzernamen und ist zum Verschicken gedacht.</p>
 */
final class SmbDiagnosticsDialog {

    private SmbDiagnosticsDialog() {
    }

    /** Prüft im Hintergrund und zeigt das Ergebnis mit „Bericht kopieren". */
    static void run(Activity activity, String url, String user, String password, String folder,
                    String file) {
        Toast.makeText(activity, R.string.smb_diagnose_running, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String report = SmbDiagnostics.report(
                    SmbDiagnostics.run(url, user, password, folder, file));
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) {
                    show(activity, report);
                }
            });
        }, "smb-diagnose").start();
    }

    private static void show(Activity activity, String report) {
        new AppDialog(activity)
                .setTitle(R.string.smb_diagnose_title)
                .setMessage(report)
                .setPositiveButton(R.string.smb_diagnose_copy, (d, w) -> copy(activity, report))
                .setNegativeButton(R.string.dialog_close, null)
                .show();
    }

    private static void copy(Activity activity, String report) {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("SMB", report));
            Toast.makeText(activity, R.string.smb_diagnose_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
