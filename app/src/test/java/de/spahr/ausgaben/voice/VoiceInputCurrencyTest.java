package de.spahr.ausgaben.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Das Währungswort in den anderen Sprachen. Bis Version 13 kannte der Parser nur „euro/eur/€" im Singular:
 * aus „peluquería 20 euros" wurde der Empfänger „peluquería s", aus „Barber 20 dollars" wurde
 * „Barber dollars". Beides sind genau die Sätze, die {@code wear_prompt} auf Spanisch und Englisch
 * vorschlägt – auf Deutsch („20 Euro", Singular) fiel es nie auf.
 */
public class VoiceInputCurrencyTest {

    private static void pruefe(String satz, String currency, String payee, long cents) {
        VoiceInput.Result r = VoiceInput.parse(satz, currency);
        assertEquals("Empfänger aus [" + satz + "]", payee, r.payee);
        assertEquals("Betrag aus [" + satz + "]", Long.valueOf(cents), r.amountCents);
    }

    @Test
    public void spanischerPluralLaesstKeinSZurueck() {
        pruefe("peluquería 20 euros", "€", "peluquería", 2000);
        pruefe("supermercado 12,50 euros", "€", "supermercado", 1250);
    }

    @Test
    public void englischeWaehrungswoerter() {
        pruefe("Barber 20 euros", "$", "Barber", 2000);
        pruefe("Barber 20 dollars", "$", "Barber", 2000);
        pruefe("Barber 20 pounds", "£", "Barber", 2000);
    }

    @Test
    public void spanischeDollarUndAkzentform() {
        pruefe("mercado 30 dólares", "$", "mercado", 3000);
        pruefe("mercado 30 dolares", "$", "mercado", 3000);
    }

    @Test
    public void deutschBleibtWieBisher() {
        pruefe("Frisör 20 Euro", "€", "Frisör", 2000);
        pruefe("Frisör 20€", "€", "Frisör", 2000);
        pruefe("Tankstelle 45.99 EUR", "€", "Tankstelle", 4599);
    }

    /** Eine hochgeladene Sprache bringt ihre Währung übers Profil mit. */
    @Test
    public void profilwaehrungWirdEntfernt() {
        pruefe("Migros 20 CHF", "CHF", "Migros", 2000);
        pruefe("coiffeur 20 francs", "CHF", "coiffeur", 2000);
    }

    /**
     * Buchstaben-Währungen nur als ganzes Wort: „kr" darf nicht mitten aus einem Namen ein Stück
     * herausreißen. „Kronor" bleibt dann lieber stehen, als daß aus „Ikea" ein Bruchstück wird.
     */
    @Test
    public void kuerzelReisstNichtsAusDemNamen() {
        pruefe("Kroneparken 20 kr", "kr", "Kroneparken", 2000);
    }

    /** Ohne Profilwährung greifen weiterhin die eingebauten Wörter. */
    @Test
    public void ohneProfilwaehrung() {
        pruefe("peluquería 20 euros", null, "peluquería", 2000);
        pruefe("Frisör 20 Euro", "", "Frisör", 2000);
    }
}
