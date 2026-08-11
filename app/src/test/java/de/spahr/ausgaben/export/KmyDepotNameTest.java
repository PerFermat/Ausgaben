package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.Account;

/**
 * Namensgleichheit zwischen einem Depot und einem gewöhnlichen Konto. In der App ist der Kontoname der
 * Schlüssel – der Name gehört deshalb dem Depot, sonst überschriebe der Typ des Namensvetters die
 * Trägerzeile und das Depot fiele aus Schublade und Kontenverwaltung.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KmyDepotNameTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private KmyDocument doc(String fixture) throws IOException {
        return new KmyDocument(KmyRobustnessTest.fixture(fixture), ctx);
    }

    private KmyImporter importer(String fixture) throws IOException {
        return new KmyImporter(doc(fixture), ctx);
    }

    @Test
    public void depotBehaeltDenSchlichtenNamen() throws IOException {
        assertTrue(doc("depot-name-clash.xml").depotNames().contains("Depot"));
    }

    @Test
    public void gleichnamigesKontoWeichtAus() throws IOException {
        List<String> namen = doc("depot-name-clash.xml").accountNames();
        assertFalse("der schlichte Name gehört dem Depot", namen.contains("Depot"));
        boolean ausgewichen = false;
        for (String name : namen) {
            ausgewichen |= name.startsWith("Depot (");
        }
        assertTrue("das gleichnamige Konto bleibt einzeln ansprechbar: " + namen, ausgewichen);
        assertTrue(namen.contains("Girokonto"));
    }

    @Test
    public void derDepotnameMeldetTypSieben() throws IOException {
        Map<String, Integer> typen = importer("depot-name-clash.xml").accountTypes();
        assertEquals(Integer.valueOf(Account.KMY_TYPE_DEPOT), typen.get("Depot"));
        assertEquals(Integer.valueOf(1), typen.get("Girokonto"));
    }

    @Test
    public void eindeutigeNamenBleibenUnveraendert() throws IOException {
        KmyDocument d = doc("institutions.xml");
        assertTrue(d.accountNames().contains("Girokonto"));
        assertTrue(d.depotNames().contains("ETF Depot"));
        assertEquals(Integer.valueOf(Account.KMY_TYPE_DEPOT),
                new KmyImporter(d, ctx).accountTypes().get("ETF Depot"));
    }
}
