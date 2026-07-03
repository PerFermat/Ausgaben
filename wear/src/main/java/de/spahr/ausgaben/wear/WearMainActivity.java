package de.spahr.ausgaben.wear;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Einziger Screen der Wear-App. Zustand A: Typ wählen (Einnahme/Umbuchung/Ausgabe) – danach hört die App
 * über die {@link SpeechRecognizer}-API automatisch zu (kein manuelles Bestätigen). Zustand B: erkannten
 * Text zeigen mit „Abbrechen (n)"; wird 10 s lang nicht abgebrochen, wird die Buchung ohne weitere Aktion
 * verarbeitet (übertragen).
 *
 * <p>Der Eintrag wird sofort lokal gespeichert (nichts geht verloren), aber erst nach Ablauf der 10 s
 * gesendet (über {@link PendingEntry#readyAt}). „Abbrechen" entfernt ihn vorher wieder.</p>
 */
public class WearMainActivity extends AppCompatActivity {

    /** Von der Kachel gesetzter Typ → App startet direkt die Sprache dafür. */
    public static final String EXTRA_TYPE = "de.spahr.ausgaben.wear.TYPE";

    private static final long CANCEL_WINDOW_MS = 10_000L;

    private PendingStore store;
    private View typeSelection;
    private View confirmView;
    private TextView status;
    private TextView confirmType;
    private TextView confirmText;
    private Button btnCancel;

    private String pendingType = WearPaths.TYPE_EXPENSE;
    private String confirmEntryId;
    private CountDownTimer confirmTimer;
    private SpeechRecognizer speech;
    private ActivityResultLauncher<String> permissionLauncher;
    private WearLocation location;
    private ActivityResultLauncher<String> locationPermissionLauncher;

    private final BroadcastReceiver pendingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_main);
        store = new PendingStore(this);
        typeSelection = findViewById(R.id.typeSelection);
        confirmView = findViewById(R.id.confirmView);
        status = findViewById(R.id.status);
        confirmType = findViewById(R.id.confirmType);
        confirmText = findViewById(R.id.confirmText);
        btnCancel = findViewById(R.id.btnCancel);

        findViewById(R.id.btnIncome).setOnClickListener(v -> chooseType(WearPaths.TYPE_INCOME));
        findViewById(R.id.btnTransfer).setOnClickListener(v -> chooseType(WearPaths.TYPE_TRANSFER));
        findViewById(R.id.btnExpense).setOnClickListener(v -> chooseType(WearPaths.TYPE_EXPENSE));
        btnCancel.setOnClickListener(v -> cancelConfirm());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startListening();
                    } else {
                        showTypeSelection();
                        showStatus(getString(R.string.wear_no_mic));
                    }
                });

        // Standort zum Sprechzeitpunkt bestimmen: Berechtigung anfragen und Empfang vorwärmen.
        location = new WearLocation(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        location.start();
                    }
                });
        if (hasLocationPermission()) {
            location.start();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Direkter Start aus der Kachel: sofort für den gewählten Typ zuhören.
        handleLaunchType(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchType(intent);
    }

    private void handleLaunchType(Intent intent) {
        if (intent == null) {
            return;
        }
        String type = intent.getStringExtra(EXTRA_TYPE);
        intent.removeExtra(EXTRA_TYPE); // nur einmal auslösen
        if (WearPaths.TYPE_INCOME.equals(type) || WearPaths.TYPE_TRANSFER.equals(type)
                || WearPaths.TYPE_EXPENSE.equals(type)) {
            chooseType(type);
        }
    }

    private void chooseType(String type) {
        pendingType = type;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    /** Programmatische Spracherkennung starten – liefert das Ergebnis automatisch (kein Bestätigen). */
    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showTypeSelection();
            showStatus(getString(R.string.wear_no_recognizer));
            return;
        }
        stopTimer();
        showListening();

        destroySpeech();
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results == null ? null
                        : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                destroySpeech();
                if (list != null && !list.isEmpty() && !list.get(0).trim().isEmpty()) {
                    onRecognized(list.get(0));
                } else {
                    onListenError();
                }
            }

            @Override
            public void onError(int error) {
                destroySpeech();
                onListenError();
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try {
            speech.startListening(intent);
        } catch (Exception e) {
            destroySpeech();
            onListenError();
        }
    }

    private void onListenError() {
        showTypeSelection();
        showStatus(getString(R.string.wear_not_understood));
    }

    /** „Höre zu"-Zustand: gleiche Fläche wie die Bestätigung, aber ohne Abbrechen/Countdown. */
    private void showListening() {
        typeSelection.setVisibility(View.GONE);
        confirmView.setVisibility(View.VISIBLE);
        confirmType.setText(typeLabel(pendingType));
        confirmType.setTextColor(typeColor(pendingType));
        confirmText.setText(R.string.wear_listening);
        btnCancel.setVisibility(View.GONE);
    }

    /** Erkannter Text → sofort lokal speichern (mit 10-s-Sperre), Bestätigung mit Countdown zeigen. */
    private void onRecognized(String text) {
        long now = System.currentTimeMillis();
        confirmEntryId = UUID.randomUUID().toString();
        // Standort zum Sprechzeitpunkt bestimmen und mitgeben.
        String gps = hasLocationPermission() ? location.currentCoordinates() : null;
        store.add(new PendingEntry(confirmEntryId, text, pendingType, gps, now, now + CANCEL_WINDOW_MS));

        confirmText.setText(text);
        btnCancel.setVisibility(View.VISIBLE);

        stopTimer();
        confirmTimer = new CountDownTimer(CANCEL_WINDOW_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secs = (millisUntilFinished + 999) / 1000;
                btnCancel.setText(getString(R.string.wear_cancel) + " (" + secs + ")");
            }

            @Override
            public void onFinish() {
                finalizeConfirm();
            }
        };
        confirmTimer.start();
    }

    /** 10 s abgelaufen → jetzt senden (readyAt ist erreicht). */
    private void finalizeConfirm() {
        stopTimer();
        confirmEntryId = null;
        WearSync.syncPending(this);
        showTypeSelection();
    }

    /** „Abbrechen" innerhalb der 10 s → Eintrag wieder entfernen. */
    private void cancelConfirm() {
        stopTimer();
        if (confirmEntryId != null) {
            store.remove(confirmEntryId);
            confirmEntryId = null;
        }
        showTypeSelection();
    }

    private void stopTimer() {
        if (confirmTimer != null) {
            confirmTimer.cancel();
            confirmTimer = null;
        }
    }

    private void destroySpeech() {
        if (speech != null) {
            speech.destroy();
            speech = null;
        }
    }

    private void showTypeSelection() {
        confirmView.setVisibility(View.GONE);
        typeSelection.setVisibility(View.VISIBLE);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, pendingReceiver,
                new IntentFilter(WearPaths.ACTION_PENDING_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (location != null && hasLocationPermission()) {
            location.start();
        }
        WearSync.syncPending(this);
        // Nicht mitten in Aufnahme/Bestätigung zurücksetzen; sonst Typauswahl zeigen.
        if (confirmTimer == null && speech == null) {
            showTypeSelection();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pendingReceiver);
        if (location != null) {
            location.stop();
        }
        // App verlassen zählt nicht als Abbrechen → Eintrag bleibt gespeichert und wird später gesendet.
        stopTimer();
        destroySpeech();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    private void updateStatus() {
        int c = store.count();
        if (c > 0) {
            status.setText(getResources().getQuantityString(R.plurals.wear_pending, c, c));
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private String typeLabel(String type) {
        if (WearPaths.TYPE_INCOME.equals(type)) {
            return getString(R.string.wear_type_income);
        }
        if (WearPaths.TYPE_TRANSFER.equals(type)) {
            return getString(R.string.wear_type_transfer);
        }
        return getString(R.string.wear_type_expense);
    }

    private int typeColor(String type) {
        if (WearPaths.TYPE_INCOME.equals(type)) {
            return ContextCompat.getColor(this, R.color.type_income);
        }
        if (WearPaths.TYPE_TRANSFER.equals(type)) {
            return ContextCompat.getColor(this, R.color.type_transfer);
        }
        return ContextCompat.getColor(this, R.color.type_expense);
    }

    /** RecognitionListener mit leeren Standard-Implementierungen; nur onResults/onError werden genutzt. */
    private abstract static class SimpleRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onPartialResults(Bundle partialResults) { }
        @Override public void onEvent(int eventType, Bundle params) { }
    }
}
