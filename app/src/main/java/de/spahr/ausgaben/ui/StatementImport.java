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
            activity.runOnUiThread(() -> resolve(activity, repository, text));
        });
    }

    /** Wertpapier über die ISIN suchen — erst in den importierten Stammdaten, dann im Gelernten. */
    private static void resolve(AppCompatActivity activity, Repository repository, PdfText text) {
        final String isin = StatementScan.isin(text);
        if (isin == null) {
            Toast.makeText(activity, R.string.statement_no_isin, Toast.LENGTH_LONG).show();
            return;
        }
        repository.getSecurityByIsin(isin, security -> {
            if (security != null) {
                start(activity, text, isin, security.depot, security.kmyId, security.name);
                return;
            }
            String[] learned = new StatementTemplates(activity).security(isin);
            if (learned != null) {
                start(activity, text, isin, learned[0], learned[1], learned[2]);
                return;
            }
            // Die ISIN ist unbekannt. Ohne Wertpapier lässt sich keine Bewegung anlegen; den Nutzer hier
            // raten zu lassen wäre schlimmer als die klare Auskunft, dass die Zuordnung fehlt.
            Toast.makeText(activity, activity.getString(R.string.statement_no_security, isin),
                    Toast.LENGTH_LONG).show();
        });
    }

    /** Vorlage anwenden (falls gelernt) und die Maske mit dem Ergebnis öffnen. */
    private static void start(AppCompatActivity activity, PdfText text, String isin,
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
