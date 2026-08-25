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

    /**
     * Das Wertpapier zur Abrechnung suchen — der Reihe nach: ISIN in den importierten Stammdaten, dann
     * im Gelernten, dann über die Kennnummer oder das Kürzel eines eigenen Wertpapiers im Text.
     */
    private static void resolve(AppCompatActivity activity, Repository repository,
                                PdfText text, Uri source) {
        final String isin = StatementScan.isin(text);
        repository.getAllSecurities(securities -> {
            if (isin != null) {
                Security byIsin = withIsin(securities, isin);
                if (byIsin != null) {
                    start(activity, text, source, isin, byIsin.depot, byIsin.kmyId, byIsin.name);
                    return;
                }
                String[] learned = new StatementTemplates(activity).security(isin);
                if (learned != null) {
                    start(activity, text, source, isin, learned[0], learned[1], learned[2]);
                    return;
                }
            }
            // Ohne ISIN oder mit einer unbekannten: kommt eines der eigenen Wertpapiere im Text vor?
            Security named = namedIn(securities, text);
            if (named != null) {
                start(activity, text, source, isin, named.depot, named.kmyId, named.name);
                return;
            }
            if (isin == null) {
                Toast.makeText(activity, R.string.statement_no_isin, Toast.LENGTH_LONG).show();
                return;
            }
            // Die ISIN ist unbekannt – in KMyMoney ist bei diesem Papier das Feld „Identifikation" nicht
            // gepflegt. Die App weiß hier nichts, was der Nutzer nicht in zwei Sekunden beantworten
            // könnte, also fragt sie einmal und merkt sich die Antwort.
            askForSecurity(activity, repository, text, source, isin);
        });
    }

    private static Security withIsin(java.util.List<Security> securities, String isin) {
        for (Security s : securities) {
            if (isin.equalsIgnoreCase(s.isin.trim())) {
                return s;
            }
        }
        return null;
    }

    /**
     * Welches der <b>eigenen</b> Wertpapiere in diesem Dokument genannt wird — über sein
     * Identifikationsfeld oder sein Kürzel. {@code null}, wenn keines oder mehrere passen.
     *
     * <p>Der Weg für amerikanische Abrechnungen: dort steht keine ISIN, sondern CUSIP und Kürzel
     * („YOU BOUGHT XBI 78464A870 …"). In KMyMoney ist die Identifikation freier Text und trägt bei einem
     * US-Papier die CUSIP; das Kürzel steht ohnehin am Wertpapier.</p>
     *
     * <p>Umgedreht gesucht — nicht „Kennnummer auslesen und nachschlagen", sondern „kommt eines meiner
     * Wertpapiere vor". Das erspart Prüfziffernrechnerei und Kürzel-Heuristik: Kandidaten sind allein die
     * eigenen Bestände, ein dreibuchstabiges Kürzel kann also nicht wild um sich greifen. Passen mehrere,
     * wird nicht geraten, sondern gefragt.</p>
     */
    static Security namedIn(java.util.List<Security> securities, PdfText text) {
        if (text == null) {
            return null;
        }
        String haystack = normalize(text.text());
        Security found = null;
        for (Security s : securities) {
            if (!mentions(haystack, s.isin, 6) && !mentions(haystack, s.symbol, 3)) {
                continue;
            }
            if (found != null && !found.kmyId.equals(s.kmyId)) {
                return null;   // mehrdeutig – dann lieber fragen
            }
            found = s;
        }
        return found;
    }

    /** Ob die Kennung als eigenes Wort im Text vorkommt; zu kurze werden nicht gesucht. */
    private static boolean mentions(String haystack, String needle, int minLength) {
        if (needle == null) {
            return false;
        }
        // normalize() polstert den Heuhaufen bereits mit Leerzeichen; die Nadel wird deshalb getrimmt
        // und hier einmal eingefasst, damit sie als eigenes Wort gesucht wird.
        String n = normalize(needle).trim();
        return n.replace(" ", "").length() >= minLength && haystack.contains(' ' + n + ' ');
    }

    /**
     * Kleinschreibung, und alles außer Buchstaben und Ziffern zu Leerzeichen. Damit findet sich ein
     * Kürzel wie {@code XAD1.DE} auch dann, wenn die Bank es anders trennt, und die Suche stolpert nicht
     * über Satzzeichen am Wortrand.
     */
    private static String normalize(String s) {
        StringBuilder out = new StringBuilder(" ");
        for (char c : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            out.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return out.append(' ').toString();
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
        pickSecurity(activity, repository, isin, isin, onPicked);
    }

    /**
     * @param label   was im Titel steht — die ISIN, oder der Dateiname, wenn der Beleg keine trägt
     * @param remember ISIN, unter der die Wahl gemerkt wird; {@code null}, wenn es nichts zu merken gibt
     */
    static void pickSecurity(AppCompatActivity activity, Repository repository, String label,
                             String remember, Repository.Callback<Security> onPicked) {
        repository.getAllSecurities(securities -> {
            if (securities.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.statement_no_security, label),
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
                    .setTitle(activity.getString(R.string.statement_pick_security, label))
                    .setItems(labels, (d, which) -> {
                        Security s = securities.get(which);
                        new StatementTemplates(activity)
                                .rememberSecurity(remember, s.depot, s.kmyId, s.name);
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
        assign(d, store, securities, text);

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

    /** Wertpapier über die ISIN, das Gelernte oder eine im Text genannte eigene Kennung. */
    private static void assign(StatementDraft d, StatementTemplates store,
                               java.util.List<Security> securities, PdfText text) {
        if (d.isin != null) {
            Security byIsin = withIsin(securities, d.isin);
            if (byIsin != null) {
                take(d, byIsin.depot, byIsin.kmyId, byIsin.name);
                return;
            }
            String[] learned = store.security(d.isin);
            if (learned != null) {
                take(d, learned[0], learned[1], learned[2]);
                return;
            }
        }
        Security named = namedIn(securities, text);
        if (named != null) {
            take(d, named.depot, named.kmyId, named.name);
        }
    }

    private static void take(StatementDraft d, String depot, String kmyId, String name) {
        d.depot = depot;
        d.kmyId = kmyId;
        d.securityName = name;
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
            markBooked(activity, repository, drafts);
        });
    }

    /**
     * Steht eine der Bewegungen schon im Depot? Erst danach öffnet die Liste — die Doppelung soll gleich
     * beim ersten Blick zu sehen sein und nicht kurz darauf nachwachsen.
     */
    private static void markBooked(AppCompatActivity activity, Repository repository,
                                   java.util.ArrayList<StatementDraft> drafts) {
        java.util.List<de.spahr.ausgaben.db.SecurityTx> candidates = new java.util.ArrayList<>();
        for (StatementDraft d : drafts) {
            candidates.add(d.isBookable() ? d.toTx() : null);
        }
        repository.findExistingSecurityTx(candidates, 0, found -> {
            for (int i = 0; i < drafts.size(); i++) {
                drafts.get(i).dupBooked = found[i];
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
