package de.spahr.ausgaben.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Die Zuordnung der gelesenen Teilbeträge zu den Kategorien der letzten Buchung.
 *
 * <p>Zwei Wege stehen hier nebeneinander, und beide haben dieselbe Zusicherung: <b>die Summe der
 * Zeilen ist der Gesamtbetrag</b>. Ginge sie verloren, stünde in KMyMoney eine Buchung, die sich
 * nicht ausgleicht — und das fiele erst dort auf.</p>
 */
public class CategorySplitsTest {

    private static CategorySplits.Part teil(String category, long cents, String label) {
        return new CategorySplits.Part(category, cents, label);
    }

    private static List<CategorySplits.Part> liste(CategorySplits.Part... parts) {
        return new ArrayList<>(Arrays.asList(parts));
    }

    /** Eine gelernte Vorlage liefert Beschriftungen; über sie findet jeder Betrag seine Kategorie. */
    @Test
    public void beschriftungFindetIhreKategorie() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, "Kapitalertragsteuer"), teil("", 873, "Solidaritätszuschlag")),
                16746,
                liste(teil("Steuern:Soli", 0, "Solidaritätszuschlag"),
                        teil("Steuern:KapESt", 0, "Kapitalertragsteuer")));
        assertEquals(2, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals(15873, out.get(0).cents);
        assertEquals("Steuern:Soli", out.get(1).category);
        assertEquals(873, out.get(1).cents);
    }

    /**
     * Fehlt in dieser Abrechnung eine Zeile — keine Kirchensteuer —, verrutscht nichts: die
     * Beschriftung sagt ja, wohin der Betrag gehört, und nicht seine Stelle in der Reihe.
     */
    @Test
    public void fehlendeZeileVerrutschtNichts() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, "Kapitalertragsteuer")),
                15873,
                liste(teil("Steuern:KapESt", 0, "Kapitalertragsteuer"),
                        teil("Steuern:Soli", 0, "Solidaritätszuschlag"),
                        teil("Steuern:Kirche", 0, "Kirchensteuer")));
        assertEquals(1, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals(15873, out.get(0).cents);
    }

    /**
     * Eine feste Ordergebühr steht in keiner Zeile der Abrechnung und hat deshalb keine Beschriftung.
     * Ihre Kategorie bringt sie trotzdem mit — und sie darf die übrigen Zeilen nicht mitreißen: ohne
     * Nachsicht fiele die ganze Aufteilung ihretwegen auf die Reihenfolge zurück, und die Steuern
     * lägen dann unter den Kategorien der Reihe nach statt unter ihren eigenen.
     */
    @Test
    public void dieFesteGebuehrOhneBeschriftungVerdirbtDenBeschriftungswegNicht() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 68071, "Kapitalertragsteuer"), teil("", 5445, "Kirchensteuer"),
                        teil("", 3743, "Solidaritätszuschlag"), teil("Ausgaben:Bank", 99, "")),
                77358,
                liste(teil("Steuern:Soli", 0, "Solidaritätszuschlag"),
                        teil("Steuern:Kirche", 0, "Kirchensteuer"),
                        teil("Steuern:KapESt", 0, "Kapitalertragsteuer"),
                        teil("Ausgaben:Bank", 0, "")));
        assertEquals(4, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals("Steuern:Kirche", out.get(1).category);
        assertEquals("Steuern:Soli", out.get(2).category);
        assertEquals("Ausgaben:Bank", out.get(3).category);
        assertEquals(77358, CategorySplits.sum(out));
    }

    /**
     * Bringt ein Teil seine Kategorie mit, gewinnt sie. Sie steht in der Erkennungsregel und ist
     * dort festgelegt worden — die Historie ist demgegenüber nur ein Schluss aus dem, was zuletzt
     * gebucht wurde.
     */
    @Test
    public void dieKategorieAusDerRegelSchlaegtDieAusDerHistorie() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("Steuern:AusDerRegel", 15873, "Kapitalertragsteuer")), 15873,
                liste(teil("Steuern:AusDerHistorie", 0, "Kapitalertragsteuer")));
        assertEquals(1, out.size());
        assertEquals("Steuern:AusDerRegel", out.get(0).category);
    }

    /** Dasselbe, wenn der Reihe nach zugeordnet wird. */
    @Test
    public void dieKategorieAusDerRegelGewinntAuchOhneBeschriftung() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("Steuern:AusDerRegel", 15873, "")), 15873,
                liste(teil("Steuern:AusDerHistorie", 0, "")));
        assertEquals("Steuern:AusDerRegel", out.get(0).category);
    }

    /** Wo die Regel schweigt, springt die Historie ein — auch innerhalb derselben Aufteilung. */
    @Test
    public void woDieRegelSchweigtSpringtDieHistorieEin() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("Steuern:KapESt", 15873, "Kapitalertragsteuer"),
                        teil("", 873, "Solidaritätszuschlag")), 16746,
                liste(teil("Aus der Historie", 0, "Solidaritätszuschlag")));
        assertEquals(2, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals("Aus der Historie", out.get(1).category);
        assertEquals(16746, CategorySplits.sum(out));
    }

    /**
     * Der Übergang: die Vorlage liefert beschriftete Teile, die zuletzt gebuchten Zeilen tragen aber
     * noch keine Beschriftung — so sehen alle Zeilen aus, die aus der ING-Klasse oder aus der Zeit
     * vor der Aufteilung stammen. Dann entscheidet die Stelle in der Reihe.
     *
     * <p>Ohne diesen Rückfall bliebe die Kategorie leer, obwohl sie danebensteht. Genau das ist beim
     * Einlesen passiert.</p>
     */
    @Test
    public void ohneBeschriftungInDerHistorieEntscheidetDieStelle() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, "Kapitalertragsteuer"), teil("", 873, "Solidaritätszuschlag")),
                16746,
                liste(teil("Steuern:KapESt", 0, ""), teil("Steuern:Soli", 0, "")));
        assertEquals(2, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals("Steuern:Soli", out.get(1).category);
        assertEquals(16746, CategorySplits.sum(out));
    }

    /**
     * Die Beschriftung behält den Vorrang, und keine Kategorie wird zweimal vergeben — sonst
     * verschöbe ein einziger Fehlschlag die ganze übrige Zuordnung.
     */
    @Test
    public void dieBeschriftungGehtVorDerStelle() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 873, "Solidaritätszuschlag"), teil("", 15873, "Fremdspesen")), 16746,
                liste(teil("Steuern:KapESt", 0, ""), teil("Steuern:Soli", 0, "Solidaritätszuschlag")));
        // Der erste trifft über seine Beschriftung, obwohl er an anderer Stelle steht.
        assertEquals("Steuern:Soli", out.get(0).category);
        // Der zweite trifft nichts und bekommt die Kategorie, die übrig ist.
        assertEquals("Steuern:KapESt", out.get(1).category);
    }

    /** Gibt es gar nichts zu erben, bleibt die Kategorie leer — der Betrag geht trotzdem nicht verloren. */
    @Test
    public void unbekannteBeschriftungOhneVorbelegungBleibtLeer() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 500, "Fremdspesen")), 500, liste());
        assertEquals(1, out.size());
        assertEquals("", out.get(0).category);
        assertEquals(500, out.get(0).cents);
    }

    /** Ein fest programmierter Leser gibt keine Beschriftungen; dann zählt die Reihenfolge. */
    @Test
    public void ohneBeschriftungGiltDieReihenfolge() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, ""), teil("", 873, "")), 16746,
                liste(teil("Steuern:KapESt", 0, ""), teil("Steuern:Soli", 0, "")));
        assertEquals(2, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals(15873, out.get(0).cents);
        assertEquals("Steuern:Soli", out.get(1).category);
        assertEquals(873, out.get(1).cents);
    }

    /**
     * Der Anfang: es gibt erst eine Kategorie, die Abrechnung aber drei Steuerzeilen. Dann landet
     * alles in dieser einen Zeile — und wer sie von Hand aufteilt, hat beim nächsten Mal drei.
     */
    @Test
    public void ueberzaehligeTeileLandenAufDerErsten() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, ""), teil("", 873, ""), teil("", 400, "")), 17146,
                liste(teil("Steuern", 0, "")));
        assertEquals(1, out.size());
        assertEquals("Steuern", out.get(0).category);
        assertEquals(17146, out.get(0).cents);
    }

    /** Gibt es noch gar keine Kategorie, steht der volle Betrag in einer Zeile ohne Kategorie. */
    @Test
    public void ohneVorbelegungEineZeileMitDerSumme() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, ""), teil("", 873, "")), 16746, liste());
        assertEquals(1, out.size());
        assertEquals("", out.get(0).category);
        assertEquals(16746, out.get(0).cents);
    }

    /**
     * Eine Aufteilung, die nicht auf die Summe aufgeht, wird nicht übernommen: dann ist entweder eine
     * Zeile übersehen worden oder der Betrag darüber wurde berichtigt. Beides ist ein Grund, es bei
     * einer Zeile zu belassen, statt eine Buchung vorzulegen, die sich nicht ausgleicht.
     */
    @Test
    public void unstimmigeAufteilungFaelltAufEineZeileZurueck() {
        List<CategorySplits.Part> out = CategorySplits.match(
                liste(teil("", 15873, "Kapitalertragsteuer")), 20000,
                liste(teil("Steuern:KapESt", 0, "Kapitalertragsteuer")));
        assertEquals(1, out.size());
        assertEquals("Steuern:KapESt", out.get(0).category);
        assertEquals(20000, out.get(0).cents);
    }

    /** Ohne Betrag gibt es nichts aufzuteilen — ein Kauf ohne Gebühr bekommt keine leere Zeile. */
    @Test
    public void ohneBetragKeineZeile() {
        assertEquals(0, CategorySplits.match(liste(), 0,
                liste(teil("Bankgebühren", 0, ""))).size());
    }

    /**
     * In der Maske steht die Kategorie trotzdem schon da: bei der Eingabe von Hand ist die letzte
     * Buchung nachgeschlagen, lange bevor ein Betrag eingetippt ist. Ohne diese Zeile müsste man die
     * Kategorie jedes Mal neu heraussuchen, obwohl die App sie längst kennt.
     */
    @Test
    public void ohneBetragStehtDieKategorieTrotzdemDa() {
        List<CategorySplits.Part> out = CategorySplits.rows(liste(), 0,
                liste(teil("Bankgebühren", 0, "")));
        assertEquals(1, out.size());
        assertEquals("Bankgebühren", out.get(0).category);
        assertEquals(0, out.get(0).cents);
    }

    /** Steht ein Betrag da, entscheidet die Zuordnung – die Vorbelegung springt nicht ein. */
    @Test
    public void mitBetragGiltDieZuordnung() {
        List<CategorySplits.Part> out = CategorySplits.rows(
                liste(teil("", 15873, ""), teil("", 873, "")), 16746,
                liste(teil("Steuern:KapESt", 0, ""), teil("Steuern:Soli", 0, "")));
        assertEquals(2, out.size());
        assertEquals(16746, CategorySplits.sum(out));
    }

    /** Ohne Vorbelegung und ohne Betrag bleibt die Liste leer — es gibt nichts zu zeigen. */
    @Test
    public void ohneAllesBleibtEsLeer() {
        assertEquals(0, CategorySplits.rows(liste(), 0, liste()).size());
    }

    /** Was auch geschieht: die Zeilen ergeben zusammen den Betrag, der darüber steht. */
    @Test
    public void dieSummeStimmtImmer() {
        long total = 16746;
        for (List<CategorySplits.Part> known : Arrays.asList(
                liste(),
                liste(teil("A", 0, "")),
                liste(teil("A", 0, "Kapitalertragsteuer"), teil("B", 0, "Solidaritätszuschlag")),
                liste(teil("A", 0, ""), teil("B", 0, ""), teil("C", 0, "")))) {
            List<CategorySplits.Part> ohne = CategorySplits.match(
                    liste(teil("", 15873, ""), teil("", 873, "")), total, known);
            assertEquals("ohne Beschriftung", total, CategorySplits.sum(ohne));
            List<CategorySplits.Part> mit = CategorySplits.match(
                    liste(teil("", 15873, "Kapitalertragsteuer"),
                            teil("", 873, "Solidaritätszuschlag")), total, known);
            assertEquals("mit Beschriftung", total, CategorySplits.sum(mit));
        }
    }
}
