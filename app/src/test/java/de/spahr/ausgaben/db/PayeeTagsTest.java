package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Die Stichwörter eines Empfängers ({@link PayeeTags}): Quellenrang, Reihenfolge, Deckelung und die
 * Frage, womit eine neue Buchung vorbelegt wird.
 */
public class PayeeTagsTest {

    /** Alias mit Stichwörtern; {@code preferred} = mit Stern. */
    private static PayeeCorrection alias(boolean preferred, String tags) {
        PayeeCorrection a = new PayeeCorrection();
        a.corrected = "REWE";
        a.preferred = preferred;
        a.tags = tags;
        return a;
    }

    @Test
    public void bevorzugterAliasStehtVorDenBuchungen() {
        List<String> tags = PayeeTags.rank(
                Collections.singletonList(alias(true, "Haushalt")),
                Arrays.asList("Urlaub", "Bahn"));
        assertEquals(Arrays.asList("Haushalt", "Urlaub", "Bahn"), tags);
    }

    @Test
    public void gewoehnlicherAliasStehtHinterDenBuchungen() {
        List<String> tags = PayeeTags.rank(
                Collections.singletonList(alias(false, "Haushalt")),
                Arrays.asList("Urlaub", "Bahn"));
        assertEquals(Arrays.asList("Urlaub", "Bahn", "Haushalt"), tags);
    }

    @Test
    public void einStichwortfeldHaeltMehrere() {
        // Anders als bei den Kategorien steht in einem Feld eine ganze Liste.
        List<String> tags = PayeeTags.rank(
                Collections.singletonList(alias(true, "Haushalt|Garten")),
                Collections.singletonList("Urlaub|Bahn"));
        assertEquals(Arrays.asList("Haushalt", "Garten", "Urlaub", "Bahn"), tags);
    }

    @Test
    public void doppelteFallenWegUndDerErsteFundZaehlt() {
        List<String> tags = PayeeTags.rank(
                Collections.singletonList(alias(true, "Urlaub")),
                Arrays.asList("urlaub", "Bahn", "URLAUB"));
        assertEquals(Arrays.asList("Urlaub", "Bahn"), tags);
    }

    @Test
    public void hoechstensSechs() {
        List<String> viele = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            viele.add("Stichwort " + i);
        }
        List<String> tags = PayeeTags.rank(null, viele);
        assertEquals(PayeeTags.LIMIT, tags.size());
        assertEquals("Stichwort 0", tags.get(0));
        assertEquals("Stichwort 5", tags.get(5));
    }

    @Test
    public void ohneAllesBleibtEsLeer() {
        assertTrue(PayeeTags.rank(null, null).isEmpty());
        assertTrue(PayeeTags.rank(new ArrayList<>(), new ArrayList<>()).isEmpty());
    }

    @Test
    public void vorbelegtWirdMitDemBevorzugtenAlias() {
        assertEquals("Haushalt|Garten", PayeeTags.preset(Arrays.asList(
                alias(false, "Sonstiges"), alias(true, "Haushalt|Garten"))));
    }

    @Test
    public void ohneSternZaehltDerErsteAliasMitStichwoertern() {
        assertEquals("Sonstiges", PayeeTags.preset(Arrays.asList(
                alias(false, ""), alias(false, "Sonstiges"), alias(false, "Später"))));
    }

    @Test
    public void dieBuchungenBelegenNichtVor() {
        // Was einmal an einem Beleg hing, soll nicht ungefragt an jedem weiteren hängen.
        assertEquals("", PayeeTags.preset(Collections.singletonList(alias(true, ""))));
        assertEquals("", PayeeTags.preset(null));
    }
}
