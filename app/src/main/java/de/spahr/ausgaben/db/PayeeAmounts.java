package de.spahr.ausgaben.db;

import java.util.Arrays;
import java.util.List;

import de.spahr.ausgaben.util.Quantile;

/**
 * Passt ein Betrag zu einem Empfänger? – das Betragssieb der Standort-Auflösung.
 *
 * <p>Stehen mehrere Empfänger dicht beieinander (Tankstelle und Waschstraße), entscheidet bisher
 * allein die Entfernung. Der Betrag weiß es oft besser: 80 € sind eine Tankfüllung und keine
 * Autowäsche. Verglichen wird gegen das <b>Rangband</b> der bisherigen Beträge dieses Empfängers –
 * standardmäßig 10 bis 90 %, also der Bereich, in dem neun von zehn seiner Buchungen liegen. In
 * Rängen statt in Beträgen gerechnet, damit ein einzelner Ausreißer das Band nicht aufreißt; die
 * Rechnung dahinter ist dieselbe wie bei den Betragsreglern der Auswertung ({@link Quantile}).</p>
 *
 * <p>Das Band ist je Alias einstellbar ({@link PayeeCorrection#pctLow}/{@link PayeeCorrection#pctHigh}).</p>
 *
 * <p>Rein rechnend, ohne Android: die Datenbankabfragen macht der Aufrufer.</p>
 */
public final class PayeeAmounts {

    /** Unter so vielen Buchungen beschreibt das Band nur den Zufall – dann gibt es kein Urteil. */
    public static final int MIN_COUNT = 5;

    /** Voreingestellter unterer Rang (Prozent). */
    public static final float DEFAULT_LOW = 10f;
    /** Voreingestellter oberer Rang (Prozent). */
    public static final float DEFAULT_HIGH = 90f;

    /**
     * Urteil über einen Betrag. Drei Werte statt ja/nein, weil die Verwendungsstellen verschieden
     * streng sind: die Automatik wirft nur {@link #MISSES} hinaus, die Vorbelegung im Editor verlangt
     * {@link #FITS}, und {@link #UNKNOWN} darf nirgends schaden.
     */
    public enum Verdict {
        /** Der Betrag liegt im Band. */
        FITS,
        /** Der Betrag liegt nachweislich daneben. */
        MISSES,
        /** Zu wenige Buchungen für ein Urteil. */
        UNKNOWN
    }

    private PayeeAmounts() {
    }

    /**
     * Liegt {@code cents} im Rangband der bisherigen Beträge?
     *
     * @param amountCents Beträge der bisherigen Buchungen dieses Empfängers (Reihenfolge egal)
     * @param cents       der zu prüfende Betrag
     * @param pctLow      unterer Rang in Prozent (0–100)
     * @param pctHigh     oberer Rang in Prozent (0–100)
     */
    public static Verdict judge(List<Long> amountCents, long cents, float pctLow, float pctHigh) {
        long[] sorted = sorted(amountCents);
        if (sorted.length < MIN_COUNT || cents <= 0) {
            return Verdict.UNKNOWN;
        }
        float low = Math.min(pctLow, pctHigh);
        float high = Math.max(pctLow, pctHigh);
        long from = Quantile.valueAt(sorted, low);
        long to = Quantile.valueAt(sorted, high);
        return cents >= from && cents <= to ? Verdict.FITS : Verdict.MISSES;
    }

    /** Wie {@link #judge} mit dem voreingestellten Band 10–90 %. */
    public static Verdict judge(List<Long> amountCents, long cents) {
        return judge(amountCents, cents, DEFAULT_LOW, DEFAULT_HIGH);
    }

    /**
     * Das Band, das für diesen Empfänger gilt: unter seinen Aliasen zählt der bevorzugte, sonst der
     * jüngste – dieselbe Rangregel wie bei der Auflösung selbst. Ohne Alias das voreingestellte Band.
     *
     * @param aliases Aliase, deren Zielname der Empfänger ist (jüngste zuerst)
     * @return {@code {pctLow, pctHigh}}
     */
    public static float[] bandOf(List<PayeeCorrection> aliases) {
        PayeeCorrection best = null;
        if (aliases != null) {
            for (PayeeCorrection a : aliases) {
                if (a == null) {
                    continue;
                }
                if (a.preferred) {
                    best = a;
                    break;                      // bevorzugter Alias schlägt alles
                }
                if (best == null) {
                    best = a;                   // sonst der erste = jüngste
                }
            }
        }
        return best == null
                ? new float[]{DEFAULT_LOW, DEFAULT_HIGH}
                : new float[]{best.pctLow, best.pctHigh};
    }

    /** Aufsteigend sortierte Beträge; {@code null}-Einträge und Nullbeträge fallen weg. */
    public static long[] sorted(List<Long> amountCents) {
        if (amountCents == null || amountCents.isEmpty()) {
            return new long[0];
        }
        long[] werte = new long[amountCents.size()];
        int n = 0;
        for (Long c : amountCents) {
            if (c != null && c > 0) {
                werte[n++] = c;
            }
        }
        long[] out = Arrays.copyOf(werte, n);
        Arrays.sort(out);
        return out;
    }
}
