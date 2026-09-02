package de.spahr.ausgaben.ui;


import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.net.Uri;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.backup.BackupCrypto;

/**
 * Der Ablauf „Sicherung einspielen" lag zeichengleich in zwei Masken und liegt jetzt einmal in
 * {@link BackupRestoreController}. Beim Zusammenlegen kann still etwas verlorengehen — die Weiche auf
 * den Passwortdialog etwa, oder die Fehlermeldung bei einer Datei, die keine Sicherung ist. Beides wird
 * hier angefasst, damit das Zusammenlegen nicht ungeprüft bleibt.
 *
 * <p>Geprüft wird der Einstieg, nicht das Einspielen selbst: was danach passiert, ersetzt die Datenbank
 * und startet die App neu — dafür ist {@code BackupStore} zuständig und dort geprüft.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupRestoreControllerTest {

    private static Uri dateiMit(byte[] inhalt) throws Exception {
        File f = File.createTempFile("sicherung", ".bin");
        f.deleteOnExit();
        Files.write(f.toPath(), inhalt);
        return Uri.fromFile(f);
    }

    /** Auf das Ergebnis warten: gelesen wird im Hintergrund, angezeigt auf dem Bedienfaden. */
    private static void warte() throws InterruptedException {
        for (int versuch = 0; versuch < 300; versuch++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (ShadowDialog.getLatestDialog() != null || ShadowToast.getTextOfLatestToast() != null) {
                return;
            }
            Thread.sleep(10);
        }
    }

    /**
     * Eine leere Maske im Erscheinungsbild der App. Absichtlich nicht {@code ProfileSettingsActivity}:
     * deren Layout lässt sich unter Robolectric nicht aufbauen (eine plattforminterne Zeichnung fehlt),
     * und für die Prüfung hier zählt allein, dass ein Fenster da ist, auf dem Dialoge stehen können.
     */
    public static class LeereMaske extends AppCompatActivity implements HostedDialog.Host {

        /**
         * Der Regler baut seine Dialoge nicht mehr selbst, sondern über {@link HostedDialog} — damit
         * sie eine Drehung überstehen. Die Maske muss ihn dafür anschließen; genau das tun
         * {@code ProfileSettingsActivity} und {@code OnboardingActivity} auch. Fehlt der Anschluss,
         * bleibt der Dialog aus — deshalb steht er hier ebenso.
         */
        BackupRestoreController regler;

        @Override
        public android.app.Dialog buildDialog(String key, android.os.Bundle args) {
            return regler == null ? null : regler.buildDialog(key, args);
        }

        @Override
        public void onDialogCancelled(String key, android.os.Bundle args) {
        }

        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            setTheme(R.style.Theme_Ausgaben);
            super.onCreate(savedInstanceState);
        }
    }

    private static LeereMaske maske() {
        return Robolectric.buildActivity(LeereMaske.class).setup().get();
    }

    /** Regler und Maske verbinden – wie in den echten Masken. */
    private static BackupRestoreController reglerIn(LeereMaske a) {
        a.regler = new BackupRestoreController(a);
        return a.regler;
    }

    @Test
    public void eineVerschluesselteSicherungFragtNachDemPasswort() throws Exception {
        LeereMaske a = maske();
        byte[] verschluesselt = BackupCrypto.encrypt(
                "kein echtes Archiv, aber verschlüsselt".getBytes(StandardCharsets.UTF_8), "geheim");
        assertTrue("Vorbedingung: die Datei gilt als verschlüsselt",
                BackupCrypto.isEncrypted(verschluesselt));

        reglerIn(a).restore(dateiMit(verschluesselt));
        warte();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue("es kommt ein Dialog", dialog != null && dialog.isShowing());
    }

    @Test
    public void eineDateiDieKeineSicherungIstWirdGemeldet() throws Exception {
        LeereMaske a = maske();

        reglerIn(a).restore(
                dateiMit("ein ganz gewöhnlicher Text".getBytes(StandardCharsets.UTF_8)));
        warte();

        // Nicht kommentarlos nichts tun: der Nutzer hat eine Datei gewählt und bekommt eine Antwort.
        String vorlage = a.getString(R.string.restore_failed, "%%");
        String anfang = vorlage.substring(0, vorlage.indexOf("%%"));
        String meldung = ShadowToast.getTextOfLatestToast();
        assertTrue("gemeldet wird das Scheitern, war aber: " + meldung,
                meldung != null && meldung.startsWith(anfang));
    }
}
