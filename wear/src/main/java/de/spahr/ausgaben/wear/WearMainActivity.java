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
    /** Reserve, bis der Standort aufgelöst ist (danach setzt updateGps readyAt auf jetzt). */
    private static final long LOCATION_WAIT_MS = 65_000L;

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

    /** Zuletzt geprüfte Erreichbarkeit des Phones; optimistisch, bis die Abfrage antwortet. */
    private boolean phoneConnected = true;

    /** Offline-Sprachmodell nur einmal je Prozess prüfen/anstoßen. */
    private static boolean offlineModelChecked;

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
            updateBalance();   // Grund bzw. Saldo folgt der Warteschlange
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
        startListening(true);
    }

    /**
     * @param allowOnDevice bei {@code true} wird – falls das Offline-Modell laut Opt-in installiert ist –
     *     der On-Device-Recognizer bevorzugt (auch online). Der automatische Online-Rückfall setzt dies auf
     *     {@code false}, damit der Standard-Recognizer greift, wenn das On-Device-Modell doch scheitert.
     */
    private void startListening(boolean allowOnDevice) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showTypeSelection();
            showStatus(getString(R.string.wear_no_recognizer));
            return;
        }
        stopTimer();
        showListening();

        destroySpeech();
        // Ist das Offline-Modell laut Opt-in installiert, hat es Vorrang – auch online (nutzt den
        // On-Device-Recognizer, der das per triggerModelDownload geladene Modell zuverlässig verwendet;
        // der Standard-Recognizer greift trotz PREFER_OFFLINE auf Wear OS oft nicht darauf zu). Ist der
        // Schalter aus oder kein Modell da, läuft die Online-Erkennung; klappt die gar nicht (kein Netz,
        // kein Modell), fällt onListenError auf den Zahlenblock zurück.
        boolean online = hasValidatedInternet();
        final boolean usingOnDevice = allowOnDevice
                && android.os.Build.VERSION.SDK_INT >= 33
                && WearStrings.installModelEnabled(this)
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if (usingOnDevice) {
            speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        } else {
            speech = SpeechRecognizer.createSpeechRecognizer(this);
        }
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
                    onListenError(SpeechRecognizer.ERROR_NO_MATCH);
                }
            }

            @Override
            public void onError(int error) {
                destroySpeech();
                // Scheitert das On-Device-Modell (z. B. noch nicht fertig geladen) und ist Internet da,
                // einmalig auf die Online-Erkennung zurückfallen statt gleich den Zahlenblock zu zeigen.
                if (usingOnDevice && hasValidatedInternet()) {
                    startListening(false);
                } else {
                    onListenError(error);
                }
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Erkennungssprache folgt der gewählten App-Sprache (auch hochgeladene). „Prefer offline" setzen,
        // wenn wir den On-Device-Recognizer nutzen oder gar kein Internet da ist.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, usingOnDevice || !online);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        // Mehrere Alternativen anfordern (die erste mit einer Zahl wird bevorzugt).
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            speech.startListening(intent);
        } catch (Exception e) {
            destroySpeech();
            onListenError(SpeechRecognizer.ERROR_CLIENT);
        }
    }

    /** BCP-47-Tag der gewählten App-Sprache; leer/unbekannt → Gerätesprache. */
    private String recognizerLanguageTag() {
        String tag = WearStrings.locale().toLanguageTag();
        if (tag == null || tag.isEmpty() || "und".equals(tag)) {
            return java.util.Locale.getDefault().toLanguageTag();
        }
        return tag;
    }

    /**
     * Hat die Uhr gerade tatsächlich nutzbares Internet? Prüft <b>alle</b> Netze (nicht nur das per-UID-
     * Default): Wear OS routet App-Verkehr standardmäßig über den Bluetooth-Proxy, der zwar {@code INTERNET}
     * meldet, aber nicht {@code VALIDATED} ist – ein daneben laufendes WLAN würde bei {@code getActiveNetwork()}
     * dann übersehen. {@code NET_CAPABILITY_VALIDATED} ist bewusst verlangt: Ist das Handy selbst offline
     * (Test 5), bleibt der BT-Proxy unvalidiert und es greift richtigerweise das Offline-Modell.
     */
    private boolean hasValidatedInternet() {
        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        for (android.net.Network net : cm.getAllNetworks()) {
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps != null
                    && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Erkennung fehlgeschlagen. Ohne nutzbares Internet (dann ist ein Sprach-Wiederholen sinnlos – ein
     * Offline-Modell hätte schon gegriffen) oder bei einem Netzwerk-/Serverfehler direkt in den stillen
     * Zahlenblock wechseln (Betrag+GPS werden gepuffert und später gesendet), damit die Eingabe <b>immer</b>
     * möglich bleibt. Nur bei vorhandenem Internet und einem reinen „nicht erkannt" → „Nicht verstanden"
     * (Sprache erneut versuchen; der Zahlenblock-Knopf steht dort ohnehin bereit).
     */
    private void onListenError(int code) {
        boolean offline = !hasValidatedInternet()
                || !phoneConnected
                || code == SpeechRecognizer.ERROR_NETWORK
                || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || code == SpeechRecognizer.ERROR_SERVER
                || code == SpeechRecognizer.ERROR_CLIENT
                || (android.os.Build.VERSION.SDK_INT >= 31
                    && code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED);
        if (offline) {
            android.widget.Toast.makeText(this, R.string.wear_offline_number,
                    android.widget.Toast.LENGTH_SHORT).show();
            showNumberPad();
        } else {
            showTypeSelection();
            showStatus(getString(R.string.wear_not_understood));
        }
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
        // text = nur Betrag → das Phone parst leeren Empfänger + Betrag → Auflösung per Standort.
        // Zunächst ohne Koordinaten und mit zurückgehaltenem readyAt ablegen; der Standort wird
        // anschließend aufgelöst (bis ~1 Min auf frischen Fix warten), dann wird gesendet.
        String id = UUID.randomUUID().toString();
        store.add(new PendingEntry(id, amt, pendingType, "",
                BalanceStore.selectedAccount(this), BalanceStore.selectedPlace(this),
                now, now + LOCATION_WAIT_MS));
        numberEntryActive = false;
        resolveLocationThenSend(id);
        showTypeSelection();
    }

    /** Löst den Standort auf (Warten auf frischen Fix / Rückfall) und sendet den Eintrag danach. */
    private void resolveLocationThenSend(String id) {
        final Context app = getApplicationContext();
        location.resolve(coords -> {
            new PendingStore(app).updateGps(id, coords == null ? "" : coords,
                    System.currentTimeMillis());
            WearSync.syncPending(app);
            // Der Grund wechselt jetzt von „Warten auf GPS" auf den Übertragungszustand.
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    refreshPhoneConnection();
                    updateBalance();
                }
            });
        });
    }

    /** Erkannter Text → sofort lokal speichern (mit 10-s-Sperre), Bestätigung mit Countdown zeigen. */
    private void onRecognized(String text) {
        long now = System.currentTimeMillis();
        confirmEntryId = UUID.randomUUID().toString();
        // Ohne Koordinaten ablegen; readyAt zurückhalten, bis nach dem 10-s-Fenster der Standort
        // aufgelöst ist (siehe finalizeConfirm). „Abbrechen" entfernt den Eintrag vorher wieder.
        store.add(new PendingEntry(confirmEntryId, text, pendingType, "",
                BalanceStore.selectedAccount(this), BalanceStore.selectedPlace(this),
                now, now + CANCEL_WINDOW_MS + LOCATION_WAIT_MS));

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

    /** 10 s abgelaufen → jetzt Standort auflösen und danach senden. */
    private void finalizeConfirm() {
        stopTimer();
        String id = confirmEntryId;
        confirmEntryId = null;
        if (id != null) {
            resolveLocationThenSend(id);
        }
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
        refreshPhoneConnection();
        if (location != null && hasLocationPermission()) {
            location.start();
        }
        WearSync.syncPending(this);
        ensureOfflineModel();
        // Nicht mitten in Aufnahme/Bestätigung/Zifferneingabe zurücksetzen; sonst Typauswahl zeigen.
        if (confirmTimer == null && speech == null && !numberEntryActive) {
            showTypeSelection();
        }
    }

    /**
     * Bei Opt-in (Phone-Einstellung „Offline-Sprachpaket auf der Uhr installieren") das Modell der gewählten
     * Sprache bereitstellen. Ab Wear OS 4 (API 33) automatisch anstoßen; auf älteren Uhren nur ein einmaliger
     * Hinweis, es manuell in den Systemeinstellungen zu laden. Ohne Opt-in passiert nichts (Offline greift
     * dann der Zahlenblock-Fallback).
     */
    private void ensureOfflineModel() {
        if (offlineModelChecked || !WearStrings.installModelEnabled(this)) {
            return;
        }
        offlineModelChecked = true;
        if (android.os.Build.VERSION.SDK_INT < 33) {
            android.widget.Toast.makeText(this, R.string.wear_model_hint,
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        triggerOfflineModelDownload();
    }

    @androidx.annotation.RequiresApi(33)
    private void triggerOfflineModelDownload() {
        final String tag = recognizerLanguageTag();
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        final SpeechRecognizer rec;
        try {
            rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        } catch (Exception e) {
            return;
        }
        try {
            rec.checkRecognitionSupport(intent, getMainExecutor(),
                    new android.speech.RecognitionSupportCallback() {
                        @Override
                        public void onSupportResult(android.speech.RecognitionSupport support) {
                            boolean installed = containsLang(support.getInstalledOnDeviceLanguages(), tag);
                            boolean pending = containsLang(support.getPendingOnDeviceLanguages(), tag);
                            boolean supported = containsLang(support.getSupportedOnDeviceLanguages(), tag);
                            if (!installed && !pending && supported) {
                                android.widget.Toast.makeText(WearMainActivity.this,
                                        R.string.wear_model_downloading,
                                        android.widget.Toast.LENGTH_SHORT).show();
                                try {
                                    rec.triggerModelDownload(intent);
                                } catch (Exception ignored) {
                                }
                            }
                            rec.destroy();
                        }

                        @Override
                        public void onError(int error) {
                            rec.destroy();
                        }
                    });
        } catch (Exception e) {
            rec.destroy();
        }
    }

    /** Grober Sprach-Vergleich: exakt oder gleicher Sprachteil (z. B. „de" ~ „de-DE"). */
    private static boolean containsLang(java.util.List<String> langs, String tag) {
        if (langs == null || tag == null) {
            return false;
        }
        String base = tag.split("-")[0].toLowerCase(java.util.Locale.ROOT);
        for (String l : langs) {
            if (l == null) {
                continue;
            }
            if (l.equalsIgnoreCase(tag)
                    || l.split("-")[0].toLowerCase(java.util.Locale.ROOT).equals(base)) {
                return true;
            }
        }
        return false;
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

    /**
     * Blendet die Statuszeile aus. Die Anzahl noch nicht übertragener Buchungen steht jetzt zusammen mit
     * dem Übertragungsgrund in einer Zeile in {@link #updateBalance()} – für einmalige Meldungen
     * (Mikrofon-Berechtigung, kein Erkenner, nicht verstanden) bleibt {@link #showStatus(String)} nutzbar.
     */
    private void updateStatus() {
        status.setVisibility(View.GONE);
    }

    /**
     * Zeigt den Konto/Ort-Saldo vom Phone; gibt es noch nicht übertragene Einträge, steht hier
     * stattdessen Anzahl + Grund in einer Zeile (nur eine Zeile Platz auf dem Rundschirm). Leer →
     * ausblenden. Wechsel-Knopf nur ab 2 Positionen.
     */
    private void updateBalance() {
        String text;
        if (BalanceStore.isRecentlySelected(this)) {
            // Innerhalb der Anzeige-Minute nach einem Wechsel-Knopf-Druck gewinnt immer das gewählte
            // Konto/Ort – auch wenn parallel noch Buchungen auf die Übertragung warten.
            text = BalanceStore.get(this);
        } else {
            text = pendingReason();
            if (text == null) {
                text = BalanceStore.get(this);
            }
        }
        if (text != null && !text.isEmpty()) {
            balanceView.setText(text);
            balanceView.setVisibility(View.VISIBLE);
        } else {
            balanceView.setVisibility(View.GONE);
        }
        btnCycle.setVisibility(BalanceStore.count(this) >= 2 ? View.VISIBLE : View.GONE);
    }

    /**
     * Warum sind Einträge noch nicht übertragen? {@code null} = nichts offen.
     * <ul>
     *   <li>{@code readyAt} in der Zukunft und noch keine Koordinaten → der Standort wird noch aufgelöst.</li>
     *   <li>Kein verbundener Knoten → das Phone ist nicht erreichbar.</li>
     *   <li>Sonst liegt der Eintrag im Data Layer und wird gerade übertragen.</li>
     * </ul>
     */
    private String pendingReason() {
        java.util.List<PendingEntry> pending = store.getPending();
        int reason = WearPendingReason.of(pending, phoneConnected);
        String label;
        switch (reason) {
            case WearPendingReason.GPS:
                label = getString(R.string.wear_reason_gps);
                break;
            case WearPendingReason.NO_PHONE:
                label = getString(R.string.wear_reason_no_phone);
                break;
            case WearPendingReason.SENDING:
                label = getString(R.string.wear_reason_sending);
                break;
            default:
                return null;
        }
        return getString(R.string.wear_pending, pending.size(), label);
    }

    /** Ist das Phone erreichbar? Ergebnis kommt asynchron und aktualisiert die Anzeige. */
    private void refreshPhoneConnection() {
        try {
            com.google.android.gms.wearable.Wearable.getNodeClient(this).getConnectedNodes()
                    .addOnSuccessListener(nodes -> {
                        phoneConnected = nodes != null && !nodes.isEmpty();
                        updateBalance();
                    })
                    .addOnFailureListener(e -> {
                        phoneConnected = false;
                        updateBalance();
                    });
        } catch (Exception ignored) {
            // Ohne Play-Services bleibt die letzte Annahme stehen.
        }
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
