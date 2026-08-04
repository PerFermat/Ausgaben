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

    @Test
    public void zweiSicherungenSindNichtIdentisch() throws Exception {
        // Salt und IV sind zufällig – gleiches Passwort ergibt trotzdem verschiedene Dateien.
        byte[] a = BackupCrypto.encrypt(PLAIN, "geheim");
        byte[] b = BackupCrypto.encrypt(PLAIN, "geheim");
        assertFalse(java.util.Arrays.equals(a, b));
    }
}
