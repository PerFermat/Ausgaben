package de.spahr.ausgaben.security;

import android.content.Context;
import android.os.Build;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.biometric.BiometricPrompt;

import de.spahr.ausgaben.R;

/**
 * Kapselt die Nutzung der androidx.biometric-Bibliothek für die optionale App-Sperre.
 *
 * <p>Es wird kein {@code CryptoObject} verwendet (reine Zugangs-Sperre), daher genügt
 * {@link Authenticators#BIOMETRIC_WEAK} – das deckt auch Gesichtserkennung ab. Zusätzlich wird
 * {@link Authenticators#DEVICE_CREDENTIAL} (PIN/Muster/Passwort) erlaubt, sofern die Kombination von der
 * API-Ebene unterstützt wird (ab Android 11 / API 30). Darunter wird nur Biometrie mit Negativ-Button
 * angeboten (kein Rückgriff auf veraltete APIs).</p>
 */
public final class BiometricAuth {

    private BiometricAuth() {
    }

    /** Erlaubte Authentifizierungsmethoden – abhängig von der API-Ebene. */
    public static int allowedAuthenticators() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL;
        }
        return Authenticators.BIOMETRIC_WEAK;
    }

    /** true, wenn die erlaubte Kombination auch das Geräte-Passwort (PIN/Muster/Passwort) einschließt. */
    public static boolean includesDeviceCredential() {
        return (allowedAuthenticators() & Authenticators.DEVICE_CREDENTIAL) != 0;
    }

    /** Roh-Status von {@link BiometricManager#canAuthenticate(int)} für die erlaubten Methoden. */
    public static int canAuthenticate(Context context) {
        return BiometricManager.from(context).canAuthenticate(allowedAuthenticators());
    }

    /** true, wenn aktuell mindestens eine unterstützte Methode verfügbar/eingerichtet ist. */
    public static boolean isAvailable(Context context) {
        return canAuthenticate(context) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** Verständliche Fehlermeldung, wenn keine Authentifizierung möglich ist ({@code null} bei Erfolg). */
    public static String availabilityMessage(Context context) {
        switch (canAuthenticate(context)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return null;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return context.getString(R.string.biometric_none_enrolled);
            default:
                // NO_HARDWARE, HW_UNAVAILABLE, SECURITY_UPDATE_REQUIRED, UNSUPPORTED, STATUS_UNKNOWN
                return context.getString(R.string.biometric_no_hardware);
        }
    }

    /** Baut die Prompt-Konfiguration; ein Negativ-Button ist nur ohne Geräte-Passwort-Fallback erlaubt. */
    public static BiometricPrompt.PromptInfo buildPromptInfo(Context context) {
        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.lock_title))
                .setSubtitle(context.getString(R.string.lock_subtitle))
                .setAllowedAuthenticators(allowedAuthenticators());
        if (!includesDeviceCredential()) {
            builder.setNegativeButtonText(context.getString(R.string.cancel));
        }
        return builder.build();
    }
}
