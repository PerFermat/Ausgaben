package de.spahr.ausgaben.backup;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Optionaler Passwortschutz für die Sicherungsdatei: Das fertige ZIP wird als Ganzes verschlüsselt.
 *
 * <p>Aufbau: {@code "AUSGBK1"} (7 Byte) + Salt (16) + IV (12) + AES-256-GCM-Chiffrat. Der Schlüssel
 * entsteht aus dem Passwort per PBKDF2-HMAC-SHA256. Der Kopf dient zugleich als Erkennungsmerkmal beim
 * Wiederherstellen – eine unverschlüsselte Sicherung beginnt mit {@code "PK"}.</p>
 */
public final class BackupCrypto {

    static final byte[] MAGIC = "AUSGBK1".getBytes(StandardCharsets.US_ASCII);

    /**
     * Kürzeste zulässige Länge eines selbstgewählten Sicherungs-Passworts.
     *
     * <p>Die Sicherung enthält den gesamten Buchungsbestand und liegt danach als Datei irgendwo — in
     * der Cloud, auf einem Stick, im Mail-Anhang. Ein vierstelliges Passwort ist trotz der 210.000
     * PBKDF2-Runden in überschaubarer Zeit durchprobiert. Kein Passwort (leeres Feld) bleibt weiterhin
     * erlaubt: das ist eine bewusste Wahl, kein Versehen.</p>
     */
    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 210_000;

    private BackupCrypto() {
    }

    /** Trägt die Datei den Kopf einer verschlüsselten Sicherung? */
    public static boolean isEncrypted(byte[] data) {
        if (data == null || data.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] encrypt(byte[] plain, String password) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(salt);
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(TAG_BITS, iv));
        byte[] enc = cipher.doFinal(plain);
        byte[] out = new byte[MAGIC.length + SALT_LEN + IV_LEN + enc.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(salt, 0, out, MAGIC.length, SALT_LEN);
        System.arraycopy(iv, 0, out, MAGIC.length + SALT_LEN, IV_LEN);
        System.arraycopy(enc, 0, out, MAGIC.length + SALT_LEN + IV_LEN, enc.length);
        return out;
    }

    /**
     * Entschlüsselt eine mit {@link #encrypt} erzeugte Datei.
     *
     * @throws GeneralSecurityException bei falschem Passwort oder beschädigter Datei
     */
    public static byte[] decrypt(byte[] file, String password) throws GeneralSecurityException {
        int head = MAGIC.length + SALT_LEN + IV_LEN;
        if (!isEncrypted(file) || file.length <= head) {
            throw new GeneralSecurityException("Keine verschlüsselte Sicherung");
        }
        byte[] salt = Arrays.copyOfRange(file, MAGIC.length, MAGIC.length + SALT_LEN);
        byte[] iv = Arrays.copyOfRange(file, MAGIC.length + SALT_LEN, head);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(file, head, file.length - head);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        char[] chars = (password == null ? "" : password).toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
