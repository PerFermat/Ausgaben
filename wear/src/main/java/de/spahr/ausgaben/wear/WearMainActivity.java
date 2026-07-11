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
import android.view.WindowManager;
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
public class WearMainActivity extends WearLocalizedActivity {

    /** Von der Kachel gesetzter Typ → App startet direkt die Sprache dafür. */
    public static final String EXTRA_TYPE = "de.spahr.ausgaben.wear.TYPE";

    private static final long CANCEL_WINDOW_MS = 10_000L;

    private PendingStore store;
    private View typeSelection;
    private View confirmView;
    private TextView status;
    private TextView balanceView;
    private View btnCycle;
    private final android.os.Handler revertHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable revertRunnable = () -> {
        BalanceStore.reset(this);
        updateBalance();
    };
    private TextView confirmType;
    private TextView confirmText;
    private Button btnCancel;
    private View btnNumberPad;
    private View numberView;
    private TextView numberDisplay;
    /** Aktuell eingegebener Betrag (stille Zifferneingabe). */
    private final StringBuilder amountInput = new StringBuilder();
    private boolean numberEntryActive;

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

    private final BroadcastReceiver balanceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBalance();
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
        balanceView = findViewById(R.id.balanceView);
        confirmType = findViewById(R.id.confirmType);
        confirmText = findViewById(R.id.confirmText);
        btnCancel = findViewById(R.id.btnCancel);

        findViewById(R.id.btnIncome).setOnClickListener(v -> chooseType(WearPaths.TYPE_INCOME));
        findViewById(R.id.btnTransfer).setOnClickListener(v -> chooseType(WearPaths.TYPE_TRANSFER));
        findViewById(R.id.btnExpense).setOnClickListener(v -> chooseType(WearPaths.TYPE_EXPENSE));
        btnCancel.setOnClickListener(v -> cancelConfirm());

        // Grauer Wechsel-Knopf: Konto/Ort durchschalten (Auto-Rücksprung nach dem Timeout).
        btnCycle = findViewById(R.id.btnCycle);
        btnCycle.setOnClickListener(v -> cycleSelection());

        // Stille Zifferneingabe (Zahlenblock).
        btnNumberPad = findViewById(R.id.btnNumberPad);
        numberView = findViewById(R.id.numberView);
        numberDisplay = findViewById(R.id.numberDisplay);
        btnNumberPad.setOnClickListener(v -> showNumberPad());
        int[] digitIds = {R.id.btnD0, R.id.btnD1, R.id.btnD2, R.id.btnD3, R.id.btnD4,
                R.id.btnD5, R.id.btnD6, R.id.btnD7, R.id.btnD8, R.id.btnD9};
        for (int id : digitIds) {
            Button b = findViewById(id);
            b.setOnClickListener(v -> appendDigit(((Button) v).getText().toString()));
        }
        findViewById(R.id.btnComma).setOnClickListener(v -> appendComma());
        findViewById(R.id.btnBack).setOnClickListener(v -> backspace());
        findViewById(R.id.btnEnter).setOnClickListener(v -> submitNumber());

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
                String best = pickBest(list);
                if (best != null) {
                    onRecognized(best);
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
        // Mehrere Alternativen anfordern (die erste mit einer Zahl wird bevorzugt).
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
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

    /** Bevorzugt die erste Erkennungs-Alternative mit einer Zahl (sonst die beste), damit ein Betrag nicht
     * verloren geht, falls das Top-Ergebnis keine Ziffer enthält. */
    private String pickBest(ArrayList<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (String s : list) {
            if (s != null && s.matches(".*\\d.*")) {
                return s.trim();
            }
        }
        String first = list.get(0) == null ? "" : list.get(0).trim();
        return first.isEmpty() ? null : first;
    }

    /** „Höre zu"-Zustand: gleiche Fläche wie die Bestätigung, aber ohne Abbrechen/Countdown. */
    private void showListening() {
        typeSelection.setVisibility(View.GONE);
        confirmView.setVisibility(View.VISIBLE);
        confirmType.setText(typeLabel(pendingType));
        confirmType.setTextColor(typeColor(pendingType));
        confirmText.setText(R.string.wear_listening);
        btnCancel.setVisibility(View.GONE);
        numberView.setVisibility(View.GONE);
        btnNumberPad.setVisibility(View.VISIBLE); // stille Zifferneingabe anbieten
        keepScreenOn(true);
    }

    /** Display während der aktiven Erfassung (Zuhören / 10-s-Timer / Zahlenblock) wach halten – kein
     * Abdunkeln/Ambient-Overlay der Uhr. */
    private void keepScreenOn(boolean on) {
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /** Zeigt den Zahlenblock (stille Eingabe); Art ist bereits gewählt. */
    private void showNumberPad() {
        stopTimer();
        destroySpeech();
        numberEntryActive = true;
        amountInput.setLength(0);
        updateNumberDisplay();
        typeSelection.setVisibility(View.GONE);
        confirmView.setVisibility(View.GONE);
        numberView.setVisibility(View.VISIBLE);
        keepScreenOn(true);
    }

    private void appendDigit(String d) {
        int comma = amountInput.indexOf(",");
        if (comma >= 0 && amountInput.length() - comma - 1 >= 2) {
            return; // max. 2 Nachkommastellen
        }
        if (amountInput.length() >= 9) {
            return;
        }
        amountInput.append(d);
        updateNumberDisplay();
    }

    private void appendComma() {
        if (amountInput.indexOf(",") >= 0) {
            return;
        }
        if (amountInput.length() == 0) {
            amountInput.append("0");
        }
        amountInput.append(",");
        updateNumberDisplay();
    }

    private void backspace() {
        if (amountInput.length() > 0) {
            amountInput.deleteCharAt(amountInput.length() - 1);
        }
        updateNumberDisplay();
    }

    private void updateNumberDisplay() {
        numberDisplay.setText(amountInput.length() == 0 ? "0" : amountInput.toString());
    }

    /** Enter: Betrag als stille Buchung ablegen (Art = gewählter Typ) und übertragen. */
    private void submitNumber() {
        String amt = amountInput.toString();
        if (amt.endsWith(",")) {
            amt = amt.substring(0, amt.length() - 1);
        }
        double val;
        try {
            val = Double.parseDouble(amt.replace(",", "."));
        } catch (NumberFormatException e) {
            return;
        }
        if (val <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        String gps = hasLocationPermission() ? location.currentCoordinates() : null;
        // text = nur Betrag → das Phone parst leeren Empfänger + Betrag → Auflösung per Standort.
        store.add(new PendingEntry(UUID.randomUUID().toString(), amt, pendingType, gps,
                BalanceStore.selectedAccount(this), BalanceStore.selectedPlace(this), now, now));
        numberEntryActive = false;
        WearSync.syncPending(this);
        showTypeSelection();
    }

    /** Erkannter Text → sofort lokal speichern (mit 10-s-Sperre), Bestätigung mit Countdown zeigen. */
    private void onRecognized(String text) {
        long now = System.currentTimeMillis();
        confirmEntryId = UUID.randomUUID().toString();
        // Standort zum Sprechzeitpunkt bestimmen und mitgeben.
        String gps = hasLocationPermission() ? location.currentCoordinates() : null;
        store.add(new PendingEntry(confirmEntryId, text, pendingType, gps,
                BalanceStore.selectedAccount(this), BalanceStore.selectedPlace(this),
                now, now + CANCEL_WINDOW_MS));

        confirmText.setText(text);
        btnCancel.setVisibility(View.VISIBLE);
        btnNumberPad.setVisibility(View.GONE);

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
        numberView.setVisibility(View.GONE);
        numberEntryActive = false;
        keepScreenOn(false);
        typeSelection.setVisibility(View.VISIBLE);
        updateStatus();
    }

    @Override
    public void onBackPressed() {
        // Aus dem Zahlenblock zurück zur Typauswahl (statt die App zu verlassen).
        if (numberEntryActive) {
            showTypeSelection();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, pendingReceiver,
                new IntentFilter(WearPaths.ACTION_PENDING_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, balanceReceiver,
                new IntentFilter(WearPaths.ACTION_BALANCE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        updateBalance();
        refreshBalanceFromDataLayer();
        if (location != null && hasLocationPermission()) {
            location.start();
        }
        WearSync.syncPending(this);
        // Nicht mitten in Aufnahme/Bestätigung/Zifferneingabe zurücksetzen; sonst Typauswahl zeigen.
        if (confirmTimer == null && speech == null && !numberEntryActive) {
            showTypeSelection();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pendingReceiver);
        unregisterReceiver(balanceReceiver);
        revertHandler.removeCallbacks(revertRunnable);
        if (location != null) {
            location.stop();
        }
        // App verlassen zählt nicht als Abbrechen → Eintrag bleibt gespeichert und wird später gesendet.
        stopTimer();
        destroySpeech();
        keepScreenOn(false);
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
            status.setText(getString(R.string.wear_pending, c));
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }

    /** Aktuellen Konto/Ort-Saldo vom Phone anzeigen; leer → ausblenden. Wechsel-Knopf nur ab 2 Positionen. */
    private void updateBalance() {
        String text = BalanceStore.get(this);
        if (text != null && !text.isEmpty()) {
            balanceView.setText(text);
            balanceView.setVisibility(View.VISIBLE);
        } else {
            balanceView.setVisibility(View.GONE);
        }
        btnCycle.setVisibility(BalanceStore.count(this) >= 2 ? View.VISIBLE : View.GONE);
    }

    /** Auf die nächste Konto/Ort-Position schalten und den Auto-Rücksprung (Timeout) neu planen. */
    private void cycleSelection() {
        BalanceStore.advance(this);
        updateBalance();
        revertHandler.removeCallbacks(revertRunnable);
        revertHandler.postDelayed(revertRunnable, BalanceStore.TIMEOUT_MS);
    }

    /**
     * Liest den aktuellen Saldo einmalig aus dem lokalen Data-Layer-Cache (billig, kein Netz) und zeigt ihn
     * an. Nötig, weil der reine Change-Listener einen bereits (unverändert) vorliegenden Saldo nicht liefert.
     */
    private void refreshBalanceFromDataLayer() {
        try {
            com.google.android.gms.wearable.Wearable.getDataClient(this).getDataItems()
                    .addOnSuccessListener(items -> {
                        try {
                            for (com.google.android.gms.wearable.DataItem item : items) {
                                if (WearPaths.PATH_BALANCE.equals(item.getUri().getPath())) {
                                    com.google.android.gms.wearable.DataMap m =
                                            com.google.android.gms.wearable.DataMapItem.fromDataItem(item)
                                                    .getDataMap();
                                    BalanceStore.save(this, m.getString("text", ""));
                                    BalanceStore.saveList(this, m.getString("list", ""));
                                }
                            }
                        } finally {
                            items.release();
                        }
                        updateBalance();
                    });
        } catch (Exception ignored) {
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
