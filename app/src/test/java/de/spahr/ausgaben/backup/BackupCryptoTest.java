package de.spahr.ausgaben.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/** Passwortschutz der Sicherungsdatei. */
public class BackupCryptoTest {

    private static final byte[] PLAIN = "PKirgendein ZIP".getBytes(StandardCharsets.UTF_8);

    @Test
    public void rundeMitRichtigemPasswort() throws Exception {
        byte[] enc = BackupCrypto.encrypt(PLAIN, "geheim");
        assertTrue(BackupCrypto.isEncrypted(enc));
        assertArrayEquals(PLAIN, BackupCrypto.decrypt(enc, "geheim"));
    }

    @Test
    public void falschesPasswortScheitert() throws Exception {
        byte[] enc = BackupCrypto.encrypt(PLAIN, "geheim");
        try {
            BackupCrypto.decrypt(enc, "daneben");
            fail("falsches Passwort muss scheitern");
        } catch (GeneralSecurityException expected) {
            // so soll es sein
        }
    }

    @Test
    public void klartextZipWirdNichtFuerVerschluesseltGehalten() {
        assertFalse(BackupCrypto.isEncrypted(PLAIN));
        assertFalse(BackupCrypto.isEncrypted(new byte[0]));
        assertFalse(BackupCrypto.isEncrypted(null));
    }

    /**
     * Ein falsches Passwort meldet sich als {@link javax.crypto.BadPaddingException} — GCM prüft beim
     * Entschlüsseln seinen Authentifizierungs-Tag, und der schlägt fehl, wenn der Schlüssel nicht passt.
     *
     * <p>Genau daran hängen die beiden Einspiel-Masken: Sie fingen bis 1.12 jede {@code Exception} und
     * meldeten stets „Passwort falsch" — auch bei einem beschädigten Archiv oder einem Lesefehler.
     * Dieser Test hält die Unterscheidung fest, auf die sie sich jetzt stützen.</p>
     */
    @Test
    public void einFalschesPasswortIstAmAusnahmetypZuErkennen() throws Exception {
        byte[] enc = BackupCrypto.encrypt(PLAIN, "geheim");
        try {
            BackupCrypto.decrypt(enc, "daneben");
            fail("falsches Passwort muss scheitern");
        } catch (javax.crypto.BadPaddingException erwartet) {
            // so soll es sein – und nur so darf „Passwort falsch" gemeldet werden
        }
    }

    /**
     * Ein beschädigtes oder fremdes Archiv scheitert <b>anders</b>. Fiele es in denselben Ausnahmetyp,
     * wäre die Unterscheidung in den Masken wertlos.
     */
    @Test
    public void einBeschaedigtesArchivIstKeinPasswortfehler() {
        for (byte[] kaputt : new byte[][]{
                PLAIN,                                   // gar keine verschlüsselte Sicherung
                BackupCrypto.MAGIC,                      // Kopf ohne Inhalt
                java.util.Arrays.copyOf(BackupCrypto.MAGIC, 30)}) {   // Kopf zu kurz
            try {
                BackupCrypto.decrypt(kaputt, "geheim");
                fail("beschädigt darf nicht durchgehen");
            } catch (javax.crypto.BadPaddingException falsch) {
                fail("das ist kein Passwortfehler: " + falsch);
            } catch (GeneralSecurityException erwartet) {
                // so soll es sein – die Maske zeigt hier den echten Grund
            }
        }
    }

    @Test
    public void zweiSicherungenSindNichtIdentisch() throws Exception {
        // Salt und IV sind zufällig – gleiches Passwort ergibt trotzdem verschiedene Dateien.
        byte[] a = BackupCrypto.encrypt(PLAIN, "geheim");
        byte[] b = BackupCrypto.encrypt(PLAIN, "geheim");
        assertFalse(java.util.Arrays.equals(a, b));
    }
}
