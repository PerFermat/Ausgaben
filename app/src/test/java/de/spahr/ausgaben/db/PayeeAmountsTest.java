package de.spahr.ausgaben.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Das Betragssieb ({@link PayeeAmounts}): Rangband, Mindestzahl, einstellbare Grenzen.
 */
public class PayeeAmountsTest {

    /** Zehn Beträge von 10 bis 100 – die Ränge liegen damit auf glatten Zehnern. */
    private static List<Long> zehner() {
        List<Long> out = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            out.add(i * 1000);
        }
        return out;
    }

    private static PayeeCorrection alias(boolean preferred, float low, float high) {
        PayeeCorrection a = new PayeeCorrection();
        a.corrected = "Waschanlage";
        a.preferred = preferred;
        a.pctLow = low;
        a.pctHigh = high;
        return a;
    }

    /** Fünf Beträge von 5 bis 10 € – mit dem Band 0–100 % genau dieser Bereich. */
    private static List<Long> fuenfBisZehn() {
        return Arrays.asList(500L, 600L, 700L, 800L, 1000L);
    }

    /** Fünf Beträge von 20 bis 100 €. */
    private static List<Long> zwanzigBisHundert() {
        return Arrays.asList(2000L, 4000L, 6000L, 8000L, 10000L);
    }

    @Test
    public void wasMittendrinLiegtPasst() {
        assertEquals(PayeeAmounts.Verdict.FITS, PayeeAmounts.judge(zehner(), 5000));
    }

    @Test
    public void wasWeitDanebenLiegtFaelltDurch() {
        assertEquals(PayeeAmounts.Verdict.MISSES, PayeeAmounts.judge(zehner(), 80_000));
        assertEquals(PayeeAmounts.Verdict.MISSES, PayeeAmounts.judge(zehner(), 100));
    }

    @Test
    public void dieBandgrenzenSelbstZaehlenNochDazu() {
        // Bei zehn Werten liegt der 10-%-Rang auf dem zweiten, der 90-%-Rang auf dem neunten.
        assertEquals(PayeeAmounts.Verdict.FITS, PayeeAmounts.judge(zehner(), 2000));
        assertEquals(PayeeAmounts.Verdict.FITS, PayeeAmounts.judge(zehner(), 9000));
        assertEquals(PayeeAmounts.Verdict.MISSES, PayeeAmounts.judge(zehner(), 1000));
        assertEquals(PayeeAmounts.Verdict.MISSES, PayeeAmounts.judge(zehner(), 10_000));
    }

    @Test
    public void einAusreisserReisstDasBandNichtAuf() {
        List<Long> mitAusreisser = new ArrayList<>(zehner());
        mitAusreisser.add(500_000L);                     // einmal 5000 € beim Bäcker
        // Der Ausreißer landet oberhalb des 90-%-Rangs und zählt damit nicht mehr zum Band.
        assertEquals(PayeeAmounts.Verdict.MISSES, PayeeAmounts.judge(mitAusreisser, 400_000));
    }

    @Test
    public void engeresBandUrteiltStrenger() {
        assertEquals(PayeeAmounts.Verdict.FITS, PayeeAmounts.judge(zehner(), 2000, 10f, 90f));
        assertEquals(PayeeAmounts.Verdict.MISSES, PayeeAmounts.judge(zehner(), 2000, 40f, 60f));
    }

    @Test
    public void vertauschteGrenzenSindKeinFehler() {
        assertEquals(PayeeAmounts.Verdict.FITS, PayeeAmounts.judge(zehner(), 5000, 90f, 10f));
    }

    @Test
    public void unterFuenfBuchungenGibtEsKeinUrteil() {
        assertEquals(PayeeAmounts.Verdict.UNKNOWN,
                PayeeAmounts.judge(Arrays.asList(1000L, 2000L, 3000L, 4000L), 999_999));
        assertEquals(PayeeAmounts.Verdict.UNKNOWN, PayeeAmounts.judge(null, 5000));
        assertEquals(PayeeAmounts.Verdict.UNKNOWN,
                PayeeAmounts.judge(Collections.<Long>emptyList(), 5000));
    }

    @Test
    public void ohneBetragGibtEsKeinUrteil() {
        assertEquals(PayeeAmounts.Verdict.UNKNOWN, PayeeAmounts.judge(zehner(), 0));
        assertEquals(PayeeAmounts.Verdict.UNKNOWN, PayeeAmounts.judge(zehner(), -500));
    }

    @Test
    public void sortierenWirftLeereWerteWeg() {
        assertArrayEquals(new long[]{1000, 2000},
                PayeeAmounts.sorted(Arrays.asList(2000L, null, 0L, 1000L, -5L)));
        assertArrayEquals(new long[0], PayeeAmounts.sorted(null));
    }

    @Test
    public void ohneAliasGiltDasVoreingestellteBand() {
        assertArrayEquals(new float[]{10f, 90f}, PayeeAmounts.bandOf(null), 0.001f);
        assertArrayEquals(new float[]{10f, 90f},
                PayeeAmounts.bandOf(Collections.<PayeeCorrection>emptyList()), 0.001f);
    }

    @Test
    public void derBevorzugteAliasBestimmtDasBand() {
        // Reihenfolge: jüngster zuerst – der bevorzugte weiter hinten gewinnt trotzdem.
        assertArrayEquals(new float[]{40f, 60f},
                PayeeAmounts.bandOf(Arrays.asList(alias(false, 0f, 100f), alias(true, 40f, 60f))),
                0.001f);
    }

    @Test
    public void ohneBevorzugtenAliasGiltDerJuengste() {
        assertArrayEquals(new float[]{20f, 80f},
                PayeeAmounts.bandOf(Arrays.asList(alias(false, 20f, 80f), alias(false, 0f, 100f))),
                0.001f);
    }

    @Test
    public void imBandIstDerAbstandEins() {
        assertEquals(1.0, PayeeAmounts.gap(fuenfBisZehn(), 700, 0f, 100f), 0.001);
        assertEquals(1.0, PayeeAmounts.gap(fuenfBisZehn(), 1000, 0f, 100f), 0.001);
    }

    @Test
    public void derAbstandZaehltImVerhaeltnis() {
        // 15 € liegt 5 € über dem einen und 5 € unter dem anderen Band – im Verhältnis ist das
        // zweite näher dran (1,33 statt 1,5).
        assertEquals(1.5, PayeeAmounts.gap(fuenfBisZehn(), 1500, 0f, 100f), 0.001);
        assertEquals(1.333, PayeeAmounts.gap(zwanzigBisHundert(), 1500, 0f, 100f), 0.001);
    }

    @Test
    public void weitDanebenIstEinGrosserAbstand() {
        // 1 € gehört zum kleinen Band, 200 € zum großen.
        assertEquals(5.0, PayeeAmounts.gap(fuenfBisZehn(), 100, 0f, 100f), 0.001);
        assertEquals(20.0, PayeeAmounts.gap(zwanzigBisHundert(), 100, 0f, 100f), 0.001);
        assertEquals(20.0, PayeeAmounts.gap(fuenfBisZehn(), 20000, 0f, 100f), 0.001);
        assertEquals(2.0, PayeeAmounts.gap(zwanzigBisHundert(), 20000, 0f, 100f), 0.001);
    }

    @Test
    public void ohneUrteilGiltDerMittlereAbstand() {
        assertEquals(PayeeAmounts.GAP_UNKNOWN,
                PayeeAmounts.gap(Arrays.asList(500L, 600L, 700L), 1500, 0f, 100f), 0.001);
        assertEquals(PayeeAmounts.GAP_UNKNOWN, PayeeAmounts.gap(null, 1500, 0f, 100f), 0.001);
        assertEquals(PayeeAmounts.GAP_UNKNOWN,
                PayeeAmounts.gap(fuenfBisZehn(), 0, 0f, 100f), 0.001);
    }
}
