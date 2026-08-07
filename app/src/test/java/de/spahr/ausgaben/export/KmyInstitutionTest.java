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
import java.util.Map;

/**
 * Bankinstitute aus dem {@code <INSTITUTIONS>}-Block: Grundlage der automatisch erzeugten
 * Bank-Kontengruppen. Konten ohne hinterlegtes Institut dürfen keine Gruppe erzeugen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KmyInstitutionTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private KmyDocument doc(String fixture) throws IOException {
        return new KmyDocument(KmyRobustnessTest.fixture(fixture), ctx);
    }

    @Test
    public void kontenBekommenIhrInstitut() throws IOException {
        Map<String, String> map = doc("institutions.xml").institutionsByAccount();
        assertEquals("Volksbank", map.get("Girokonto"));
        assertEquals("LBS", map.get("Bausparen"));
    }

    @Test
    public void auchDepotsBekommenIhrInstitut() throws IOException {
        Map<String, String> map = doc("institutions.xml").institutionsByAccount();
        assertEquals("Volksbank", map.get("ETF Depot"));
    }

    @Test
    public void kontoOhneInstitutErzeugtKeineGruppe() throws IOException {
        Map<String, String> map = doc("institutions.xml").institutionsByAccount();
        assertFalse(map.containsKey("Bargeld"));
    }

    @Test
    public void dateiOhneInstituteLiefertNichts() throws IOException {
        assertTrue(doc("empty-blocks.xml").institutionsByAccount().isEmpty());
    }
}
