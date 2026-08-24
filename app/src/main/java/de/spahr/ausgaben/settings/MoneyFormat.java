package de.spahr.ausgaben.settings;

import android.content.Context;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Zentrale Betragsformatierung für die gesamte App (und – über den vom Handy formatierten Uhr-Saldo –
 * für die Uhr). Das gewählte Zahlenformat (Tausender-/Dezimalzeichen, Gruppierung) und die Option
 * „Währungskennzeichen anzeigen" werden aus {@link SettingsStore} in einen statischen Zustand geladen
 * ({@link #refresh(Context)}) und von {@link #display(long, String)}/{@link #plain(long)} genutzt.
 *
 * <p>Standard = heutiges Verhalten (keine Tausendertrennung, Dezimalkomma, Währung an), damit sich ohne
 * bewusste Auswahl nichts ändert.</p>
 */
public final class MoneyFormat {

    private static volatile char groupingSep = '.';
    private static volatile char decimalSep = ',';
    private static volatile boolean grouping = false;
    private static volatile boolean showCurrency = true;

    private MoneyFormat() {
    }

    /** Lädt Zahlenformat + Währungsanzeige aus den Einstellungen (synchron, nur SharedPreferences). */
    public static void refresh(Context context) {
        SettingsStore s = new SettingsStore(context.getApplicationContext());
        applyFormat(s.getNumberFormat());
        showCurrency = s.isCurrencyShown();
    }

    private static void applyFormat(String format) {
        switch (format) {
            case SettingsStore.NUMBER_FORMAT_EN_GROUP:
                groupingSep = ',';
                decimalSep = '.';
                grouping = true;
                break;
            case SettingsStore.NUMBER_FORMAT_PLAIN_DOT:
                decimalSep = '.';
                grouping = false;
                break;
            case SettingsStore.NUMBER_FORMAT_DE_GROUP:
                groupingSep = '.';
                decimalSep = ',';
                grouping = true;
                break;
            case SettingsStore.NUMBER_FORMAT_PLAIN_COMMA:
            default:
                decimalSep = ',';
                grouping = false;
                break;
        }
    }

    /**
     * Das eingestellte Dezimalzeichen (Komma oder Punkt). Gilt nicht nur für die Anzeige, sondern auch
     * für die Eingabe: die Rechentastatur beschriftet ihre Taste damit, und die Betragsfelder nehmen
     * genau dieses Zeichen an – nicht beide.
     */
    public static char decimalSeparator() {
        return decimalSep;
    }

    /**
     * Dezimalzahl (Kurs, Stückzahl, Prozent) mit dem <b>eingestellten</b> Dezimalzeichen und ohne Währung.
     * Nachlaufende Nullen fallen weg, mindestens {@code minDecimals} bleiben stehen. Nötig, weil solche
     * Zahlen sonst fest mit Komma erschienen, während die Beträge daneben je nach Einstellung einen Punkt
     * zeigen – auf dem Depot-Bildschirm stünde dann „1234.56 €" neben „(3,45 %)".
     */
    public static String decimal(double value, int minDecimals, int maxDecimals) {
        String s = String.format(Locale.US, "%." + maxDecimals + "f", value);
        int dot = s.indexOf('.');
        if (dot >= 0) {
            int end = s.length();
            while (end - dot - 1 > minDecimals && s.charAt(end - 1) == '0') {
                end--;
            }
            if (s.charAt(end - 1) == '.') {
                end--;
            }
            s = s.substring(0, end);
        }
        return s.replace('.', decimalSep);
    }

    /**
     * Nachkommastellen einer Stückzahl. Sechs, weil Banken Sparplan-Anteile fein abrechnen: die ING weist
     * {@code 6,09607} und {@code 1.839,80185} aus, also fünf Stellen – mit vier verschwände die letzte
     * still, und beim Export stünde eine falsche Stückzahl in der KMyMoney-Datei.
     */
    public static final int SHARE_DECIMALS = 6;

    /** Eine Stückzahl im eingestellten Zahlenformat; nachlaufende Nullen fallen weg. */
    public static String shares(double value) {
        return decimal(value, 0, SHARE_DECIMALS);
    }

    /** Ob das Währungskennzeichen angehängt wird – für Werte, die nicht über {@link #display} laufen. */
    public static boolean isCurrencyShown() {
        return showCurrency;
    }

    /** Anzeige-String für ein Label: Betrag im gewählten Format + Währung, falls aktiviert und vorhanden. */
    public static String display(long cents, String currency) {
        String num = number(cents, grouping);
        if (showCurrency && currency != null && !currency.trim().isEmpty()) {
            return num + " " + currency.trim();
        }
        return num;
    }

    /** Betrag ohne Tausendertrennung und ohne Währung – für editierbare Felder (bleibt parser-sicher). */
    public static String plain(long cents) {
        return number(cents, false);
    }

    private static String number(long cents, boolean group) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setDecimalSeparator(decimalSep);
        sym.setGroupingSeparator(groupingSep);
        DecimalFormat df = new DecimalFormat(group ? "#,##0.00" : "0.00", sym);
        df.setGroupingUsed(group);
        // Vorzeichen (inkl. „-0,50") übernimmt DecimalFormat selbst.
        return df.format(BigDecimal.valueOf(cents, 2));
    }
}
