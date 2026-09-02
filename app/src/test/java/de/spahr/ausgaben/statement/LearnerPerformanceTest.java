package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Der Lerner muss auch an einem langen Beleg in erträglicher Zeit fertig werden.
 *
 * <p>Die Summensuche zählte alle Zweier- und Dreierkombinationen der Zeilen mit brauchbarer letzter Zahl
 * auf, ohne jede Beschneidung — Θ(n³). Der teure Fall ist nicht der Treffer, sondern der <b>Nicht</b>-
 * Treffer: kommt der gesuchte Betrag als Summe gar nicht vor, läuft der Suchraum vollständig durch. Und
 * das Lernen läuft nach dem Speichern einer Buchung, also im Vordergrund.</p>
 *
 * <p>Der Umfang hier ist bewusst gross gewählt, und dazu gehört die ehrliche Einordnung: gemessen auf
 * dem Entwicklungsrechner brauchte die alte Fassung bei 900 Posten etwa 1,1 Sekunden — spürbar, aber
 * kein Stillstand. Erst der kubische Anstieg macht daraus etwas anderes: bei 2500 Posten sind es 28
 * Sekunden, die neue Fassung braucht dafür 0,3. Der Test greift diese Grössenordnung ab; er sichert die
 * Beschneidung, nicht eine bestimmte Zahl von Millisekunden.</p>
 */
public class LearnerPerformanceTest {

    /**
     * Ein Beleg mit vielen bezifferten Posten, deren Beträge sich paarweise und zu dritt <b>nicht</b> zum
     * gesuchten Betrag addieren: alle Posten sind Vielfache von 10 Cent, der gesuchte Betrag endet auf
     * einen ungeraden Cent. Damit ist jede Kombination auszuschließen — die Frage ist nur, wie schnell.
     */
    private static PdfText langerBeleg(int posten) {
        List<String> zeilen = new ArrayList<>();
        zeilen.add("Wertpapierabrechnung Kauf");
        zeilen.add("Nominale Stück 10,00000");
        for (int i = 0; i < posten; i++) {
            zeilen.add(String.format(java.util.Locale.GERMANY, "Steuerposten %s EUR %d,%d0",
                    beschriftung(i), 100 + i, i % 10));
        }
        return PdfText.fromLines(String.join("\n", zeilen));
    }

    /**
     * Eine Beschriftung aus Buchstaben, gemischt geschrieben. Beides ist nötig, damit der Test
     * überhaupt misst, was er messen soll: Die Beschriftung einer Zeile endet vor dem ersten Wort mit
     * einer Ziffer, „Posten0001" ergäbe also gar keine; drei Grossbuchstaben wiederum gelten als
     * Währungskürzel und fallen am Zeilenende genauso weg wie das „EUR" dahinter. In beiden Fällen
     * trügen alle Zeilen dieselbe Beschriftung, und weil der Lerner gleiche Beschriftungen nur einmal
     * wertet, bliebe von tausenden Zeilen ein einziger Kandidat übrig — der Test liefe an der
     * Summensuche vorbei und wäre auch mit der alten Fassung grün. Genau das war er im ersten Anlauf.
     */
    private static String beschriftung(int i) {
        return "" + (char) ('A' + i / 676 % 26) + (char) ('a' + i / 26 % 26) + (char) ('a' + i % 26);
    }

    @Test(timeout = 5_000)
    public void einLangerBelegHaengtDenLernerNicht() {
        PdfText text = langerBeleg(2500);
        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.BUY;
        bekannt.shares = 10.0;
        bekannt.netCents = 777_777L;   // steht nirgends und ist auch keine Summe von zwei oder drei

        StatementTemplate gelernt = TemplateLearner.learn(text, bekannt);

        // Was dabei herauskommt, ist hier nebensächlich – geprüft wird, dass es überhaupt herauskommt.
        assertNotNull(gelernt);
    }
}
