package de.spahr.ausgaben.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Welche drei Buchstaben ein Währungskürzel sind — und welche nicht.
 *
 * <p>An drei Stellen der Abrechnungserkennung wurde bisher schlicht „drei Großbuchstaben" geprüft.
 * Das ist zu weit gefasst und ändert Beträge: {@code 1437STK} („Stück") wurde zu {@code 1437} und
 * {@code 2019DEC} zu {@code 2019}, weil die vermeintliche Währung abgeschnitten wurde. Solche
 * Scheinzahlen wandern anschließend als Kandidaten in die Regelsuche und verschieben dort die
 * abgezählte Stelle — der Fehler zeigt sich also nicht dort, wo er entsteht.</p>
 *
 * <p>Geprüft wird deshalb gegen die tatsächlich vergebenen Kürzel nach ISO 4217. Die Liste ist
 * vollständig statt auf die üblichen Verdächtigen beschränkt: eine Abrechnung kann in jeder Währung
 * ausgestellt sein, und ein fehlendes Kürzel fiele als „Betrag nicht erkannt" auf, während ein zuviel
 * aufgenommenes still falsch rechnet.</p>
 */
public final class Currencies {

    private static final Set<String> CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN",
            "BAM", "BBD", "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BOV",
            "BRL", "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHE", "CHF",
            "CHW", "CLF", "CLP", "CNY", "COP", "COU", "CRC", "CUP", "CVE", "CZK",
            "DJF", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB", "EUR", "FJD", "FKP",
            "GBP", "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL",
            "HTG", "HUF", "IDR", "ILS", "INR", "IQD", "IRR", "ISK", "JMD", "JOD",
            "JPY", "KES", "KGS", "KHR", "KMF", "KPW", "KRW", "KWD", "KYD", "KZT",
            "LAK", "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD",
            "MMK", "MNT", "MOP", "MRU", "MUR", "MVR", "MWK", "MXN", "MXV", "MYR",
            "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB", "PEN",
            "PGK", "PHP", "PKR", "PLN", "PYG", "QAR", "RON", "RSD", "RUB", "RWF",
            "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SOS", "SRD",
            "SSP", "STN", "SVC", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP",
            "TRY", "TTD", "TWD", "TZS", "UAH", "UGX", "USD", "USN", "UYI", "UYU",
            "UYW", "UZS", "VED", "VES", "VND", "VUV", "WST", "XAF", "XAG", "XAU",
            "XCD", "XDR", "XOF", "XPD", "XPF", "XPT", "XSU", "XUA", "YER", "ZAR",
            "ZMW", "ZWG",
            // Abgelöst, steht aber noch auf älteren Belegen und in alten .kmy-Beständen.
            "ATS", "DEM", "FRF", "ITL", "NLG", "BEF", "ESP", "FIM", "GRD", "PTE",
            "IEP", "LUF", "HRK", "LTL", "LVL", "EEK", "SKK", "SIT", "CYP", "MTL")));

    private Currencies() {
    }

    /** Ob {@code s} genau ein vergebenes Währungskürzel ist — drei Großbuchstaben genügen nicht. */
    public static boolean isCode(String s) {
        return s != null && s.length() == 3 && CODES.contains(s);
    }
}
