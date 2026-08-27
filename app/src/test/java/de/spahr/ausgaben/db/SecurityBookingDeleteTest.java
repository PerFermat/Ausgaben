package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Eine Wertpapier-Buchung im Geldkonto löschen — und was dabei mitgehen muss.
 *
 * <p>Zwei Zeilen gehören zusammen: die Geldbuchung und die Depot-Bewegung. Bliebe eine ohne die andere
 * stehen, stimmte der Depotwert nicht mehr mit dem Kontostand zusammen. Steht die Buchung bereits in der
 * KMyMoney-Datei, muss die Löschung außerdem dorthin durchschlagen — dafür wird sie vorgemerkt.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityBookingDeleteTest {

    private Context ctx;
    private AppDatabase db;
    private Repository repo;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        // Die Vormerkung für die Datei entsteht nur im kmy-Modus – im CSV-Betrieb gibt es keine
        // gemeinsame Datei, aus der etwas zu entfernen wäre.
        new SettingsStore(ctx).save("", "", "", "", "", "", SettingsStore.MODE_KMY, "", "");
        executor = Executors.newSingleThreadExecutor();
        db = AppDatabase.getInstance(ctx);
        imHintergrund(() -> {
            db.clearAllTables();
            return null;
        });
        repo = new Repository(ctx);
    }

    @After
    public void tearDown() throws Exception {
        imHintergrund(() -> {
            db.clearAllTables();
            return null;
        });
        executor.shutdownNow();
    }

    /**
     * Die App-Datenbank verbietet Zugriffe vom Hauptfaden – zu Recht. Der Test hält sich daran und legt
     * seine Testdaten auf demselben Weg an, den die App benutzt.
     */
    private <T> T imHintergrund(Callable<T> arbeit) throws Exception {
        return executor.submit(arbeit).get(10, TimeUnit.SECONDS);
    }

    private static long tag(int jahr, int monat, int tag, int stunde) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag, stunde, 0);
        return c.getTimeInMillis();
    }

    /** Ein Wertpapierkauf im Geldkonto: Umbuchung vom Girokonto ins Wertpapier. */
    private Booking kauf(boolean exportiert) throws Exception {
        Booking b = new Booking();
        b.account = "Girokonto";
        b.isTransfer = true;
        b.transferAccount = "Vanguard FTSE All-World";
        b.payee = "Vanguard FTSE All-World";
        b.isIncome = false;
        b.amountCents = 100_250L;
        b.createdAt = tag(2026, 8, 19, 9);
        b.exported = exportiert;
        b.id = imHintergrund(() -> db.bookingDao().insert(b));
        return b;
    }

    private SecurityTx bewegung(long bookingId, boolean vorgemerkt) throws Exception {
        SecurityTx tx = new SecurityTx();
        tx.depot = "Depot";
        tx.securityKmyId = "S000001";
        tx.securityName = "Vanguard FTSE All-World";
        tx.moneyAccount = "Girokonto";
        tx.action = "buy";
        tx.shares = 6.09607;
        tx.amountCents = 100_250L;
        tx.netCents = 100_250L;
        tx.date = tag(2026, 8, 19, 0);
        tx.pending = vorgemerkt;
        tx.bookingId = bookingId;
        tx.id = imHintergrund(() -> db.securityDao().insertTx(tx));
        return tx;
    }

    /**
     * Löscht auf dem Hintergrund-Faden. Der asynchrone Weg meldet sich über den Hauptfaden zurück – und
     * genau den blockiert der Test, solange er wartet. Deshalb dieselbe synchrone Fassung wie in
     * {@code AccountGroupMembershipTest}.
     */
    private void loesche(Booking booking) throws Exception {
        imHintergrund(() -> {
            repo.deleteSecurityBookingNow(booking);
            return null;
        });
    }

    private List<KmyPendingDelete> vormerkungen() throws Exception {
        return imHintergrund(() -> db.kmyPendingDeleteDao().getAll());
    }

    private Booking buchung(long id) throws Exception {
        return imHintergrund(() -> db.bookingDao().getById(id));
    }

    private SecurityTx bewegungMitId(long id) throws Exception {
        return imHintergrund(() -> db.securityDao().getTxById(id));
    }

    @Test
    public void eineNochNichtExportierteBuchungNimmtDieBewegungMit() throws Exception {
        Booking b = kauf(false);
        SecurityTx tx = bewegung(b.id, true);

        loesche(b);

        assertNull("die Buchung blieb stehen", buchung(b.id));
        assertNull("die Bewegung blieb stehen", bewegungMitId(tx.id));
        assertTrue("für eine unexportierte Buchung darf nichts vorgemerkt werden",
                vormerkungen().isEmpty());
    }

    @Test
    public void eineExportierteBuchungWirdAuchInDerDateiVorgemerkt() throws Exception {
        Booking b = kauf(true);
        SecurityTx tx = bewegung(b.id, false);

        loesche(b);

        assertNull(buchung(b.id));
        assertNull("auch die exportierte Bewegung muss weg", bewegungMitId(tx.id));

        List<KmyPendingDelete> offen = vormerkungen();
        assertEquals(1, offen.size());
        assertEquals("Girokonto", offen.get(0).account);
        assertEquals("Ausgabe: der Kontosplit steht negativ in der Datei",
                -100_250L, offen.get(0).signedCents);
        assertEquals(tag(2026, 8, 19, 9), offen.get(0).createdAt);
    }

    /**
     * Der häufige Fall: Depot und Konto wurden getrennt eingelesen, die Bewegung kennt die Buchung also
     * gar nicht. Zugeordnet wird dann über Wertpapier, Konto, Tag und Betrag.
     */
    @Test
    public void eineEingeleseneBewegungWirdUeberDenInhaltGefunden() throws Exception {
        Booking b = kauf(true);
        SecurityTx tx = bewegung(0, false);

        loesche(b);

        assertNull(bewegungMitId(tx.id));
    }

    @Test
    public void eineFremdeBewegungBleibtUnberuehrt() throws Exception {
        Booking b = kauf(true);
        SecurityTx fremd = bewegung(0, false);
        fremd.securityName = "iShares Core MSCI World";
        imHintergrund(() -> {
            db.securityDao().updateTx(fremd);
            return null;
        });

        loesche(b);

        assertNull(buchung(b.id));
        assertNotNull("eine fremde Bewegung darf nicht mitgelöscht werden",
                bewegungMitId(fremd.id));
    }

    @Test
    public void eineGewoehnlicheBuchungOhneBewegungWirdNurSelbstGeloescht() throws Exception {
        Booking b = new Booking();
        b.account = "Girokonto";
        b.payee = "Bäcker";
        b.amountCents = 250L;
        b.createdAt = tag(2026, 8, 19, 9);
        b.exported = true;
        b.id = imHintergrund(() -> db.bookingDao().insert(b));
        SecurityTx unbeteiligt = bewegung(0, false);

        loesche(b);

        assertNull(buchung(b.id));
        assertNotNull("keine Umbuchung – da ist keine Bewegung zuzuordnen",
                bewegungMitId(unbeteiligt.id));
    }
}
