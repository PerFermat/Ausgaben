package de.spahr.ausgaben.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * Charakterisierungstests für {@link VoiceInput} – die Sprach-Auflösung (AliasResolver.resolve) baut auf
 * {@code parse} und {@code bestFuzzyPayee}. Halten das aktuelle Verhalten fest.
 */
public class VoiceInputTest {

    @Test
    public void parse_amountWithCurrencySymbol() {
        VoiceInput.Result r = VoiceInput.parse("Frisör 20€");
        assertEquals(Long.valueOf(2000), r.amountCents);
        assertEquals("Frisör", r.payee);
    }

    @Test
    public void parse_decimalWithEuroWord() {
        VoiceInput.Result r = VoiceInput.parse("Rewe 12,50 Euro");
        assertEquals(Long.valueOf(1250), r.amountCents);
        assertEquals("Rewe", r.payee);
    }

    @Test
    public void parse_dotDecimalWithEur() {
        VoiceInput.Result r = VoiceInput.parse("Tankstelle 45.99 EUR");
        assertEquals(Long.valueOf(4599), r.amountCents);
        assertEquals("Tankstelle", r.payee);
    }

    @Test
    public void parse_noAmount() {
        VoiceInput.Result r = VoiceInput.parse("Kaffee");
        assertNull(r.amountCents);
        assertEquals("Kaffee", r.payee);
    }

    @Test
    public void parse_null() {
        VoiceInput.Result r = VoiceInput.parse(null);
        assertNull(r.amountCents);
        assertEquals("", r.payee);
    }

    @Test
    public void parse_amountOnly() {
        VoiceInput.Result r = VoiceInput.parse("5");
        assertEquals(Long.valueOf(500), r.amountCents);
        assertEquals("", r.payee);
    }

    @Test
    public void fuzzy_identicalTokenMatches() {
        assertEquals("Rewe Süd", VoiceInput.bestFuzzyPayee("rewe", Arrays.asList("Rewe Süd", "Aldi")));
    }

    @Test
    public void fuzzy_umlautNormalization() {
        assertEquals("Baecker Meier",
                VoiceInput.bestFuzzyPayee("Bäcker", Collections.singletonList("Baecker Meier")));
    }

    @Test
    public void fuzzy_noSimilarCandidateReturnsNull() {
        assertNull(VoiceInput.bestFuzzyPayee("xyzabc", Collections.singletonList("Rewe")));
    }

    @Test
    public void fuzzy_shortTermIgnored() {
        // Tokens unter 4 Zeichen zählen nicht → kein Treffer.
        assertNull(VoiceInput.bestFuzzyPayee("abc", Collections.singletonList("abcd")));
    }

    @Test
    public void fuzzy_nullSafe() {
        assertNull(VoiceInput.bestFuzzyPayee(null, Collections.singletonList("Rewe")));
        assertNull(VoiceInput.bestFuzzyPayee("Rewe", null));
    }
}
