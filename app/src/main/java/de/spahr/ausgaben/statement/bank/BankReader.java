package de.spahr.ausgaben.statement.bank;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Ein von Hand geschriebener Leser für die Abrechnungen <b>einer</b> Bank.
 *
 * <p>Die gelernte Ankerlogik ({@code TemplateLearner}) muss ohne jede Kenntnis der Bank auskommen und
 * liest gemessen rund 38 % der Belege vollständig. Wo ein Beleg vorliegt, geht es besser: dann steht
 * fest, wie die Bank ihre Abrechnung baut, und ein Leser holt alle Werte statt einiger.</p>
 *
 * <p>Beides zusammen, nicht statt: der Leser hat Vorrang, die gelernte Vorlage füllt, was er offen
 * lässt, und für jede Bank ohne Leser bleibt allein die Ankerlogik. Deshalb ist ein Leser kein Ersatz
 * für sie, sondern eine Zugabe für die Banken, die jemand tatsächlich hat.</p>
 *
 * <p><b>Woher ein Leser kommt:</b> aus einem echten Beleg der Bank, den ein Nutzer beisteuert. Nicht aus
 * dem Bestand von Portfolio Performance — der steht unter der EPL, die mit der GPL dieser App
 * unvereinbar ist. Zum Messen von außen gelesen werden darf er ({@code PpCorpus}), als Vorlage für Code
 * taugt er nicht, und sein Inhalt gehört nicht in dieses Verzeichnis.</p>
 *
 * <p><b>Eine Bank nachrüsten:</b> eine Klasse hier anlegen, eine Zeile in {@link BankReaders} eintragen,
 * einen Test danebenlegen. Mehr ist es nicht.</p>
 */
public interface BankReader {

    /** Kurzname für Protokoll und Test, etwa {@code "ing"}. */
    String id();

    /**
     * Ist das eine Abrechnung dieser Bank?
     *
     * <p>Lieber ein Nein zu viel: sagt ein Leser fälschlich zu, liest er mit den Beschriftungen einer
     * fremden Bank und legt stillschweigend falsche Zahlen vor. Sagt er zu Unrecht ab, übernimmt die
     * Ankerlogik — das ist der harmlose Fehler von beiden.</p>
     */
    boolean matches(PdfText text);

    /**
     * Trägt ein, was in der Abrechnung steht. Was nicht dasteht, bleibt {@code null} — <b>geraten wird
     * nichts</b>, dafür ist später die Maske da.
     */
    void read(PdfText text, StatementTemplate.Extraction into);
}
