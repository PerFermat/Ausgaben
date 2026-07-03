package de.spahr.ausgaben.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import de.spahr.ausgaben.AusgabenApp;
import de.spahr.ausgaben.R;
import de.spahr.ausgaben.security.BiometricAuth;

/**
 * Sperrbildschirm der optionalen App-Sperre. Zeigt den {@link BiometricPrompt} (Fingerabdruck, Gesicht,
 * PIN, Muster oder Passwort). Bei Erfolg wird die App entsperrt; bei Abbruch geschlossen.
 *
 * <p>{@code AppCompatActivity} ist eine {@code FragmentActivity} – Voraussetzung für den androidx-Prompt.
 * Der Prompt nutzt intern ein beibehaltenes Fragment, das Konfigurationswechsel (Rotation) überdauert;
 * deshalb wird {@code authenticate()} nur beim Erststart aufgerufen (nicht bei Wiederherstellung).</p>
 */
public class LockActivity extends AppCompatActivity {

    private BiometricPrompt biometricPrompt;
    private TextView message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        message = findViewById(R.id.lockMessage);
        findViewById(R.id.btnUnlock).setOnClickListener(v -> authenticate());

        // Zurück-Taste umgeht die Sperre nicht → App schließen (bleibt gesperrt).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                ((AusgabenApp) getApplication()).markUnlocked();
                finish();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                // Bewusster Abbruch → App schließen; sonst gesperrt bleiben (Retry möglich).
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                        || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        || errorCode == BiometricPrompt.ERROR_CANCELED) {
                    finishAffinity();
                } else {
                    message.setText(errString);
                }
            }
        });

        // Rotation/Neustart: das beibehaltene Prompt-Fragment zeigt den Dialog weiterhin an.
        if (savedInstanceState == null) {
            authenticate();
        }
    }

    private void authenticate() {
        message.setText(R.string.lock_subtitle);
        biometricPrompt.authenticate(BiometricAuth.buildPromptInfo(this));
    }
}
