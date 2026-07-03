package de.spahr.ausgaben.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Einziger Screen der Wear-App: Titel, großer Mikrofon-Button und darunter der Status
 * (zuletzt erkannter Text bzw. „x Buchungen noch nicht übertragen").
 *
 * <p>Nach der Spracheingabe wird der Text lokal gespeichert (PENDING) und sofort ein Übertragungsversuch
 * gestartet. Übertragung und Zähler-Aktualisierung laufen vollautomatisch – auch später über
 * {@link WearMessageListenerService} bei Wiederverbindung.</p>
 */
public class WearMainActivity extends AppCompatActivity {

    public static final String EXTRA_START_VOICE = "start_voice";

    private PendingStore store;
    private TextView status;
    private String lastRecognized;
    private ActivityResultLauncher<Intent> voiceLauncher;

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
        status = findViewById(R.id.status);
        findViewById(R.id.micButton).setOnClickListener(v -> startVoice());

        voiceLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> spoken = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (spoken != null && !spoken.isEmpty()) {
                            handleRecognized(spoken.get(0));
                        }
                    }
                });

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_START_VOICE, false)) {
            startVoice();
        }
    }

    private void startVoice() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.wear_prompt));
        try {
            voiceLauncher.launch(intent);
        } catch (Exception e) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.wear_no_recognizer);
        }
    }

    private void handleRecognized(String text) {
        lastRecognized = text;
        store.add(new PendingEntry(UUID.randomUUID().toString(), text, System.currentTimeMillis()));
        WearSync.syncPending(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, pendingReceiver,
                new IntentFilter(WearPaths.ACTION_PENDING_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        WearSync.syncPending(this);
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pendingReceiver);
    }

    private void updateStatus() {
        int c = store.count();
        StringBuilder sb = new StringBuilder();
        if (lastRecognized != null && !lastRecognized.isEmpty()) {
            sb.append(lastRecognized);
        }
        if (c > 0) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(getResources().getQuantityString(R.plurals.wear_pending, c, c));
        }
        status.setText(sb.toString());
        status.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);
    }
}
