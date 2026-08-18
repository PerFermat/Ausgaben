package de.spahr.ausgaben.voice;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;

import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Gemeinsame Konfiguration der Freiform-Spracherkennung (Handy). Bündelt den {@link RecognizerIntent},
 * den bislang {@code MainActivity} und {@code VoiceCaptureActivity} wortgleich bauten – damit Sprache,
 * Offline-Verhalten usw. an einer Stelle stehen (früher lief hier ein fest verdrahtetes „de-DE"
 * auseinander).
 */
public final class VoiceRecognizer {

    private VoiceRecognizer() {
    }

    /**
     * Baut den Freiform-Erkennungs-Intent. Erkennungssprache folgt der gewählten App-Sprache (auch
     * hochgeladene); nicht unterstützte Codes fallen im System auf die Gerätesprache zurück. Es werden
     * mehrere Alternativen angefordert – die erste mit lesbarem Betrag wird bevorzugt
     * ({@link #pickBestSpoken(List)}).
     */
    public static Intent freeFormIntent(Context ctx) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new SettingsStore(ctx).getLanguage());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.voice_prompt));
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        return intent;
    }

    /**
     * Wählt aus den Erkennungs-Alternativen die erste, aus der sich ein Betrag lesen lässt (sonst die
     * erste), damit ein Betrag nicht verloren geht, falls das Top-Ergebnis keine Zahl enthält.
     */
    public static String pickBestSpoken(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        for (String s : list) {
            if (s != null && VoiceInput.parse(s).amountCents != null) {
                return s;
            }
        }
        return list.get(0);
    }
}
