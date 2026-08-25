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
        pickSecurity(activity, repository, isin,
                s -> start(activity, text, source, isin, s.depot, s.kmyId, s.name));
    }

    /**
     * Legt alle Wertpapiere zur Wahl und merkt sich die Antwort zur ISIN. Ab dann wird nicht mehr
     * gefragt — auch dann nicht, wenn die Identifikation in KMyMoney weiterhin fehlt. Gibt es überhaupt
     * kein Wertpapier, bleibt nur die Auskunft; zu wählen wäre dann nichts.
     */
    static void pickSecurity(AppCompatActivity activity, Repository repository, String isin,
                             Repository.Callback<Security> onPicked) {
        repository.getAllSecurities(securities -> {
            if (securities.isEmpty()) {
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
            // Die ISIN steht im Titel, nicht als Nachricht: ein Dialog zeigt entweder eine Nachricht
            // oder eine Liste – mit setMessage bliebe die Auswahl unsichtbar.
            new AppDialog(activity)
                    .setTitle(activity.getString(R.string.statement_pick_security, isin))
                    .setItems(labels, (d, which) -> {
                        Security s = securities.get(which);
                        new StatementTemplates(activity)
                                .rememberSecurity(isin, s.depot, s.kmyId, s.name);
                        onPicked.onResult(s);
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
        // Nur vorhanden, wenn auf der Regelseite eine Brutto-Regel angelegt wurde (Dollar-Papiere).
        if (e.grossCents != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_GROSS, e.grossCents);
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

    // ---- Mehrere Abrechnungen auf einmal ----

    /**
     * Liest einen ganzen Stapel Abrechnungen und legt das Ergebnis zur Durchsicht vor
     * ({@link StatementBatchActivity}).
     *
     * <p>Eine einzelne Datei geht weiter den kurzen Weg direkt in die Maske. Das ist kein Sonderfall aus
     * Bequemlichkeit: dort lernt die App beim Speichern, wo die Werte in den Abrechnungen dieser Bank
     * stehen, und genau dafür ist die erste Abrechnung da. Bei einem Stapel käme diese Rückfrage je Datei
     * — dort zählt der Überblick, nicht das Lernen.</p>
     */
    static void openAll(final AppCompatActivity activity, final Repository repository,
                        final java.util.List<Uri> uris) {
        if (uris.isEmpty()) {
            return;
        }
        if (uris.size() == 1) {
            open(activity, repository, uris.get(0));
            return;
        }
        Toast.makeText(activity, activity.getString(R.string.statement_batch_reading, uris.size()),
                Toast.LENGTH_SHORT).show();
        // Die Wertpapiere einmal holen statt je Datei: zugeordnet wird danach im Speicher.
        repository.getAllSecurities(securities -> repository.executor().execute(() -> {
            StatementTemplates store = new StatementTemplates(activity);
            java.util.ArrayList<StatementDraft> drafts = new java.util.ArrayList<>();
            for (int i = 0; i < uris.size(); i++) {
                drafts.add(read(activity, store, securities, uris.get(i), i));
            }
            activity.runOnUiThread(() -> fillDefaults(activity, repository, drafts));
        }));
    }

    /**
     * Eine Datei zum Entwurf. Was sich nicht lesen lässt, wird nicht übergangen, sondern als Eintrag mit
     * Grund vermerkt — sonst suchte der Nutzer später eine Buchung, die es nie gab.
     */
    private static StatementDraft read(AppCompatActivity activity, StatementTemplates store,
                                       java.util.List<Security> securities, Uri uri, int index) {
        StatementDraft d = new StatementDraft();
        d.fileName = displayName(activity, uri);
        final PdfText text;
        try {
            text = PdfTextExtractor.read(activity, uri);
        } catch (Exception e) {
            d.failure = R.string.statement_unreadable;
            return d;
        }
        if (!text.hasText()) {
            d.failure = R.string.statement_no_text;
            return d;
        }
        d.isin = StatementScan.isin(text);
        assign(d, store, securities);

        StatementTemplate template = store.match(text);
        StatementTemplate.Extraction e = template != null ? template.apply(text) : templateFree(text);
        d.action = e.action;
        d.dateMillis = e.dateMillis;
        d.shares = e.shares;
        d.price = e.price;
        d.feeCents = e.feeCents;
        d.netCents = e.netCents;
        d.grossCents = e.grossCents;

        File staged = de.spahr.ausgaben.receipt.SingleReceipt.stage(activity, uri);
        if (staged != null) {
            d.stagedPath = staged.getAbsolutePath();
        }
        // Je Eintrag ein eigener Zwischenspeicher: die Maske greift beim Berichtigen darauf zu, und ein
        // gemeinsamer Name zeigte dort auf die zuletzt gelesene Datei statt auf die bearbeitete.
        d.textPath = cache(activity, text, "statement_" + index + ".txt");
        return d;
    }

    /** Wertpapier über die ISIN — erst in den importierten Stammdaten, dann im Gelernten. */
    private static void assign(StatementDraft d, StatementTemplates store,
                               java.util.List<Security> securities) {
        if (d.isin == null) {
            return;
        }
        for (Security s : securities) {
            if (d.isin.equalsIgnoreCase(s.isin)) {
                d.depot = s.depot;
                d.kmyId = s.kmyId;
                d.securityName = s.name;
                return;
            }
        }
        String[] learned = store.security(d.isin);
        if (learned != null) {
            d.depot = learned[0];
            d.kmyId = learned[1];
            d.securityName = learned[2];
        }
    }

    /**
     * Gegenkonto und Kategorien für alle Entwürfe auf einmal nachschlagen, die Zahlen ergänzen und die
     * Liste öffnen.
     */
    private static void fillDefaults(AppCompatActivity activity, Repository repository,
                                     java.util.ArrayList<StatementDraft> drafts) {
        java.util.List<String[]> keys = new java.util.ArrayList<>();
        for (StatementDraft d : drafts) {
            keys.add(d.kmyId.isEmpty() || d.action == null
                    ? null : new String[]{d.depot, d.kmyId, d.action});
        }
        repository.getSecurityTxDefaultsBatch(keys, defaults -> {
            for (int i = 0; i < drafts.size(); i++) {
                StatementDraft d = drafts.get(i);
                de.spahr.ausgaben.db.SecurityTx last = defaults.get(i);
                if (last != null) {
                    d.moneyAccount = last.moneyAccount;
                    d.feeCategory = last.feeCategory;
                    d.incomeCategory = last.incomeCategory;
                }
                d.resolve();
            }
            Intent i = new Intent(activity, StatementBatchActivity.class);
            i.putParcelableArrayListExtra(StatementBatchActivity.EXTRA_DRAFTS, drafts);
            activity.startActivity(i);
        });
    }

    /** Der Name, unter dem der Nutzer die Datei ausgewählt hat — die einzige Kennung einer Datei,
     * die sich nicht lesen ließ. */
    private static String displayName(AppCompatActivity activity, Uri uri) {
        try (android.database.Cursor c = activity.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Nicht jeder Anbieter liefert einen Namen; dann tut der Pfad es auch.
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
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
        return cache(activity, text, CACHE_NAME);
    }

    private static String cache(AppCompatActivity activity, PdfText text, String name) {
        try {
            File file = new File(activity.getCacheDir(), name);
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
