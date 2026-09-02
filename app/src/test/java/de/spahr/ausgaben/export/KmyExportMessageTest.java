package de.spahr.ausgaben.export;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Was am Ende eines Exports dasteht — und dass die Depot-Bewegungen darin mitzählen.
 *
 * <p>Sie nehmen einen eigenen Weg durch den Exporter und stehen deshalb in einer eigenen
 * Ergebnisliste. Gezählt wurden bis 1.13 aber nur die gewöhnlichen Buchungen: Wer eine einzelne
 * Wertpapierbuchung übertrug, las „0 Buchung(en) geschrieben", während die Transaktion längst in der
 * Datei stand. Eine Meldung, die dem eigenen Ergebnis widerspricht, ist schlimmer als keine — man
 * exportiert dann ein zweites Mal.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KmyExportMessageTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private String meldung(int buchungen, int bewegungen) {
        KmyExporter.Result res = new KmyExporter.Result();
        for (long i = 1; i <= buchungen; i++) {
            res.writtenIds.add(i);
        }
        return KmyExportCoordinator.buildMessage(ctx, res, bewegungen, 0, 0,
                "michael.kmy", "Backup/michael.kmy.bak");
    }

    /** Der Fall aus dem Bericht: nichts als eine Wertpapierbuchung. */
    @Test
    public void eineEinzelneDepotBewegungWirdAlsBuchungGezaehlt() {
        assertTrue("Meldung nennt die Bewegung nicht: " + meldung(0, 1),
                meldung(0, 1).contains("1"));
        assertTrue("es steht weiterhin 0 da: " + meldung(0, 1), !meldung(0, 1).contains("0 "));
    }

    /** Und zusammen mit gewöhnlichen Buchungen werden beide addiert. */
    @Test
    public void buchungenUndBewegungenZaehlenZusammen() {
        assertTrue("2 + 3 sollten 5 ergeben: " + meldung(2, 3), meldung(2, 3).contains("5"));
    }

    /** Ohne Bewegungen bleibt alles, wie es war. */
    @Test
    public void ohneBewegungenBleibtDieZaehlungWieBisher() {
        assertTrue(meldung(4, 0).contains("4"));
    }
}
