package de.spahr.ausgaben.receipt;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Der Name, unter dem eine Belegseite in die ausgegebene ZIP-Datei wandert:
 * {@code <jjjj-mm-tt>_<Empfänger>_<Betrag>_<Buchungsnummer>_p<n>.<endung>}, also etwa
 * {@code 2026-08-16_Bäckerei-Mayer_8_23_1487_p1.jpg}.
 *
 * <p>In der App heißen Belege nach einer UUID – für die App genug, für einen Menschen unbrauchbar.
 * Wer die Belege eines Quartals weitergibt, soll ihnen ansehen, wozu sie gehören.</p>
 *
 * <p>Die Buchungsnummer steht <b>immer</b> im Namen: zwei Einkäufe am selben Tag beim selben Bäcker
 * über denselben Betrag sind nichts Ungewöhnliches, und in einer ZIP-Datei wäre der zweite Beleg
 * sonst still verloren.</p>
 *
 * <p>Kennt kein Android und ist damit ohne Emulator prüfbar.</p>
 */
public final class ReceiptExportName {

    /** So lang darf der Empfängerteil werden; der Rest fällt weg, damit der Name handhabbar bleibt. */
    private static final int PAYEE_MAX = 60;

    /** Für eine Buchung ohne Empfänger – ohne Umlaut, damit der Ersatzname überall gleich ankommt. */
    private static final String NO_PAYEE = "ohne-Empfaenger";

    /** In Dateinamen unzulässig (Windows ist hier strenger als Linux); alles davon wird zu «-». */
    private static final String FORBIDDEN = "/\\:*?\"<>|";

    private ReceiptExportName() {
    }

    /**
     * Der Eintragsname für eine Belegseite.
     *
     * @param createdAt   Buchungsdatum in Millisekunden
     * @param payee       Empfänger der Buchung
     * @param amountCents Betrag in Cent (Vorzeichen gleichgültig)
     * @param bookingId   Nummer der Buchung – macht den Namen eindeutig
     * @param pageFile    Dateiname der Seite in der App, etwa {@code abc_p2.pdf}; daraus kommen
     *                    Seitennummer und Endung
     */
    public static String of(long createdAt, String payee, long amountCents, long bookingId,
                            String pageFile) {
        // Den Seitenzusatz bildet NoteReceipt selbst, damit beide Schreibweisen im Gleichschritt bleiben.
        return NoteReceipt.pageName(
                date(createdAt) + "_" + payee(payee) + "_" + amount(amountCents) + "_" + bookingId,
                NoteReceipt.pageOf(pageFile),
                NoteReceipt.isPdf(pageFile) ? NoteReceipt.PDF : NoteReceipt.JPG);
    }

    /**
     * Der Eintragsname einer Wertpapier-Abrechnung:
     * {@code <jjjj-mm-tt>_<Art>_<Wertpapier>_<Betrag>_<Buchungsnummer>_p<n>.pdf}, also etwa
     * {@code 2026-09-01_Kauf_Vanguard-FTSE-All-World_500_00_12232_p1.pdf}.
     *
     * <p>Warum hier nicht der Empfänger steht wie sonst: Die Geldbuchung einer Depot-Bewegung geht
     * <b>ohne</b> Empfänger in die KMyMoney-Datei — so hält es KMyMoney bei eigenen Wertpapierbuchungen
     * auch. Nach dem nächsten Import hieße jeder solche Beleg deshalb „ohne-Empfaenger". Bewegungsart und
     * Wertpapiername stehen dagegen an der Bewegung selbst und sagen ohnehin mehr.</p>
     *
     * @param action       die Bewegungsart, bereits übersetzt („Kauf", „Verkauf", „Dividende")
     * @param securityName Name des Wertpapiers
     */
    public static String ofSecurity(long createdAt, String action, String securityName,
                                    long amountCents, long bookingId, String pageFile) {
        return NoteReceipt.pageName(
                date(createdAt) + "_" + payee(action) + "_" + payee(securityName) + "_"
                        + amount(amountCents) + "_" + bookingId,
                NoteReceipt.pageOf(pageFile),
                NoteReceipt.isPdf(pageFile) ? NoteReceipt.PDF : NoteReceipt.JPG);
    }

    /** Buchungsdatum als {@code jjjj-mm-tt} – so sortiert der Dateimanager von selbst richtig. */
    public static String date(long createdAt) {
        // Locale.US: es geht um die Ziffern, nicht um eine Landesschreibweise.
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(createdAt));
    }

    /**
     * Der Betrag ohne Vorzeichen, mit {@code _} statt des Dezimalzeichens: 823 → {@code 8_23}.
     *
     * <p>Bewußt <b>nicht</b> über {@code MoneyFormat}: der Dateiname soll nicht mit der eingestellten
     * Zahlenschreibweise wandern und keine Tausendertrennung tragen.</p>
     */
    public static String amount(long cents) {
        long abs = Math.abs(cents);
        return abs / 100 + "_" + String.format(Locale.US, "%02d", abs % 100);
    }

    /**
     * Der Empfänger, tauglich für einen Dateinamen: Leerzeichen und unzulässige Zeichen werden zu
     * {@code -}, mehrere davon zu einem zusammengezogen. <b>Umlaute bleiben</b> – die ZIP-Einträge
     * werden UTF-8 geschrieben.
     */
    public static String payee(String name) {
        if (name == null) {
            return NO_PAYEE;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            boolean bad = c < ' ' || c == ' ' || c == '_' || FORBIDDEN.indexOf(c) >= 0;
            char out = bad ? '-' : c;
            // Kein doppelter Trenner und keiner am Anfang.
            if (out == '-' && (sb.length() == 0 || sb.charAt(sb.length() - 1) == '-')) {
                continue;
            }
            sb.append(out);
            if (sb.length() >= PAYEE_MAX) {
                break;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        return sb.length() == 0 ? NO_PAYEE : sb.toString();
    }
}
