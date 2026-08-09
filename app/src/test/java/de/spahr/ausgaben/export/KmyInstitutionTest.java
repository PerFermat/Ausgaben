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

/**
 * Die aus der Datei abgeleiteten Kontengruppen: Bankinstitute aus dem {@code <INSTITUTIONS>}-Block und
 * „Favoriten" aus dem Paar {@code PreferredAccount} im Konto-Block. Konten ohne hinterlegtes Institut
 * dürfen keine Gruppe erzeugen.
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

    @Test
    public void bevorzugteKontenWerdenErkannt() throws IOException {
        List<String> favoriten = doc("institutions.xml").favoriteAccounts();
        assertTrue(favoriten.contains("Girokonto"));
        // Depots sind in der App gewöhnliche Konten und dürfen ebenso Favorit sein.
        assertTrue(favoriten.contains("ETF Depot"));
        assertEquals(2, favoriten.size());
    }

    @Test
    public void nurJaZaehltAlsFavorit() throws IOException {
        List<String> favoriten = doc("institutions.xml").favoriteAccounts();
        assertFalse(favoriten.contains("Bausparen")); // trägt „No"
        assertFalse(favoriten.contains("Bargeld"));   // trägt gar kein Kennzeichen
    }

    @Test
    public void dateiOhneFavoritenLiefertNichts() throws IOException {
        assertTrue(doc("empty-blocks.xml").favoriteAccounts().isEmpty());
    }
}
