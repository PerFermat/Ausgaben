package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.db.Security;
import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.pdf.PdfTextExtractor;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.StatementScan;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Der Weg von einer PDF-Abrechnung der Bank zur vorbelegten Erfassungsmaske.
 *
 * <p>Ausgelesen wird nur, was sicher erkannt ist: die ISIN (mit Prüfziffer) und — sobald für diese Bank
 * eine Vorlage gelernt ist — die Zahlen an ihren Ankern. <b>Geraten wird nichts</b>; was offen bleibt,
 * bleibt leer und wird von Hand ergänzt. Genau diese Ergänzung bringt der App beim Speichern bei, wo die
 * Werte stehen (siehe {@code SecurityTxEditActivity}).</p>
 *
 * <p>Gebucht wird hier nichts — das Ergebnis ist eine Vorbelegung, die der Nutzer sieht und bestätigt.</p>
 */
final class StatementImport {

    /** Dateiname des zwischengespeicherten Textes; die Maske lernt daraus beim Speichern. */
    static final String CACHE_NAME = "statement.txt";

    private StatementImport() {
    }

    /**
     * Liest das PDF und öffnet die Maske. Läuft auf dem Executor des Repositories — ein mehrseitiges
     * Dokument braucht spürbar Zeit, und der Hauptthread darf dabei nicht stehen.
     */
    static void open(final AppCompatActivity activity, final Repository repository, final Uri uri) {
        Toast.makeText(activity, R.string.statement_reading, Toast.LENGTH_SHORT).show();
        repository.executor().execute(() -> {
            final PdfText text;
            try {
                text = PdfTextExtractor.read(activity, uri);
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, R.string.statement_unreadable, Toast.LENGTH_LONG).show());
                return;
            }
            if (!text.hasText()) {
                // Eingescannt: ohne Texterkennung ist da nichts zu holen. Das gehört gesagt, nicht
                // verschwiegen – sonst sucht der Nutzer den Fehler bei sich.
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, R.string.statement_no_text, Toast.LENGTH_LONG).show());
                return;
            }
            activity.runOnUiThread(() -> resolve(activity, repository, text, uri));
        });
    }

    /** Wertpapier über die ISIN suchen — erst in den importierten Stammdaten, dann im Gelernten. */
    private static void resolve(AppCompatActivity activity, Repository repository,
                                PdfText text, Uri source) {
        final String isin = StatementScan.isin(text);
        if (isin == null) {
            Toast.makeText(activity, R.string.statement_no_isin, Toast.LENGTH_LONG).show();
            return;
        }
        repository.getSecurityByIsin(isin, security -> {
            if (security != null) {
                start(activity, text, source, isin, security.depot, security.kmyId, security.name);
                return;
            }
            String[] learned = new StatementTemplates(activity).security(isin);
            if (learned != null) {
                start(activity, text, source, isin, learned[0], learned[1], learned[2]);
                return;
            }
            // Die ISIN ist unbekannt – in KMyMoney ist bei diesem Papier das Feld „Identifikation" nicht
            // gepflegt. Die App weiß hier nichts, was der Nutzer nicht in zwei Sekunden beantworten
            // könnte, also fragt sie einmal und merkt sich die Antwort.
            askForSecurity(activity, repository, text, source, isin);
        });
    }

    /**
     * Fragt einmalig, zu welchem Wertpapier die Abrechnung gehört, und merkt sich die Zuordnung zur ISIN.
     * Ab dann wird sie nicht mehr gefragt — auch dann nicht, wenn die Identifikation in KMyMoney weiterhin
     * fehlt.
     */
    private static void askForSecurity(AppCompatActivity activity, Repository repository,
                                       PdfText text, Uri source, String isin) {
        repository.getAllSecurities(securities -> {
            if (securities.isEmpty()) {
                // Ohne ein einziges Wertpapier gibt es nichts zu wählen; dann bleibt nur die Auskunft.
                Toast.makeText(activity, activity.getString(R.string.statement_no_security, isin),
                        Toast.LENGTH_LONG).show();
                return;
            }
            boolean severalDepots = severalDepots(securities);
            CharSequence[] labels = new CharSequence[securities.size()];
            for (int i = 0; i < securities.size(); i++) {
                Security s = securities.get(i);
                // Bei mehreren Depots gehört das Depot dazu – sonst wäre bei gleichnamigen Papieren nicht
                // zu unterscheiden, welches gemeint ist.
                labels[i] = severalDepots ? s.name + "  ·  " + s.depot : s.name;
            }
            new AppDialog(activity)
                    .setTitle(R.string.statement_pick_security)
                    .setMessage(activity.getString(R.string.statement_no_security, isin))
                    .setItems(labels, (d, which) -> {
                        Security s = securities.get(which);
                        new StatementTemplates(activity)
                                .rememberSecurity(isin, s.depot, s.kmyId, s.name);
                        start(activity, text, source, isin, s.depot, s.kmyId, s.name);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private static boolean severalDepots(java.util.List<Security> securities) {
        String first = securities.get(0).depot;
        for (Security s : securities) {
            if (!s.depot.equals(first)) {
                return true;
            }
        }
        return false;
    }

    /** Vorlage anwenden (falls gelernt) und die Maske mit dem Ergebnis öffnen. */
    private static void start(AppCompatActivity activity, PdfText text, Uri source, String isin,
                              String depot, String kmyId, String name) {
        StatementTemplates store = new StatementTemplates(activity);
        StatementTemplate template = store.match(text);
        StatementTemplate.Extraction e = template != null
                ? template.apply(text) : templateFree(text);

        Intent i = new Intent(activity, SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, depot);
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, kmyId);
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, name);
        if (e.action != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_ACTION, e.action);
        }
        if (e.dateMillis > 0) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_DATE, e.dateMillis);
        }
        if (e.shares != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_SHARES, e.shares);
        }
        if (e.price != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_PRICE, e.price);
        }
        if (e.feeCents != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_FEE, e.feeCents);
        }
        if (e.netCents != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_NET, e.netCents);
        }
        // Die Abrechnung wandert schon jetzt in die Belegablage – beim Speichern wird sie dort zum Beleg
        // der Gegenbuchung, und bis dahin lässt sie sich in der Maske ansehen.
        java.io.File staged = de.spahr.ausgaben.receipt.SingleReceipt.stage(activity, source);
        if (staged != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_FILE, staged.getAbsolutePath());
        }
        // Der Text bleibt für die Sitzung liegen: beim Speichern leitet die Maske daraus die Anker ab.
        String cached = cache(activity, text);
        if (cached != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_TEXT, cached);
            i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_ISIN, isin);
        }
        activity.startActivity(i);
    }

    /** Ohne gelernte Vorlage bleibt der Aktions-Vorschlag und die ISIN — mehr wird nicht geraten. */
    private static StatementTemplate.Extraction templateFree(PdfText text) {
        StatementTemplate.Extraction e = new StatementTemplate.Extraction();
        e.action = StatementScan.guessAction(text);
        e.isin = StatementScan.isin(text);
        return e;
    }

    /** Legt den Text im Zwischenspeicher ab; {@code null}, wenn das nicht geht (dann wird nicht gelernt). */
    private static String cache(AppCompatActivity activity, PdfText text) {
        try {
            File file = new File(activity.getCacheDir(), CACHE_NAME);
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                w.write(text.text());
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }
}
