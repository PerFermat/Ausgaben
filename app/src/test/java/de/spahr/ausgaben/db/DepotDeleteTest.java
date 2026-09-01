package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * „Konto löschen" für ein Depot: bisher gar nicht möglich (Depots fehlten in der Liste), deshalb
 * blieb ein einmal angelegtes Depot für immer stehen – auch nach einem Werksreset auf ein neues,
 * leeres Profil sah es wie „das Depot aus dem falschen Profil" aus.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DepotDeleteTest {

    private Context ctx;
    private AppDatabase db;
    private Repository repo;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
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

    private <T> T imHintergrund(Callable<T> arbeit) throws Exception {
        return executor.submit(arbeit).get(10, TimeUnit.SECONDS);
    }

    private void legeDepotAn(String depot, String kmyId) throws Exception {
        Account a = new Account(depot);
        imHintergrund(() -> {
            db.accountDao().insertIfAbsent(a);
            db.accountDao().setType(depot, Account.KMY_TYPE_DEPOT);
            return null;
        });
        Security s = new Security();
        s.depot = depot;
        s.kmyId = kmyId;
        s.name = "Vanguard FTSE All-World";
        imHintergrund(() -> {
            db.securityDao().insertSecurity(s);
            return null;
        });
        SecurityTx tx = new SecurityTx();
        tx.depot = depot;
        tx.securityKmyId = kmyId;
        tx.securityName = s.name;
        tx.moneyAccount = "Girokonto";
        tx.action = "buy";
        tx.shares = 6.09607;
        tx.amountCents = 100_250L;
        tx.netCents = 100_250L;
        tx.date = 0L;
        imHintergrund(() -> {
            db.securityDao().insertTx(tx);
            return null;
        });
    }

    private boolean accountExists(String name) throws Exception {
        return imHintergrund(() -> db.accountDao().getActiveNames().contains(name));
    }

    private List<SecurityTx> transaktionen(String depot, String kmyId) throws Exception {
        return imHintergrund(() -> db.securityDao().getTxBySecurity(depot, kmyId));
    }

    /**
     * {@code deleteAccountsAndDepots} läuft auf Repositorys eigenem Hintergrund-Executor und meldet
     * sich per {@code onDone} auf dem Hauptfaden zurück – deshalb hier mit CountDownLatch warten und
     * den (in Robolectric standardmäßig pausierten) Hauptfaden zwischendurch antreiben.
     */
    private void loescheUndWarte(List<String> accounts, List<String> depots) throws Exception {
        java.util.concurrent.CountDownLatch fertig = new java.util.concurrent.CountDownLatch(1);
        repo.deleteAccountsAndDepots(accounts, depots, fertig::countDown);
        for (int i = 0; i < 50 && fertig.getCount() > 0; i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            fertig.await(20, TimeUnit.MILLISECONDS);
        }
        assertEquals("onDone wurde nicht rechtzeitig aufgerufen", 0, fertig.getCount());
    }

    @Test
    public void loeschtDepotSamtWertpapierenUndBewegungen() throws Exception {
        legeDepotAn("Depot A", "S1");

        loescheUndWarte(Collections.emptyList(), Collections.singletonList("Depot A"));

        assertTrue("Depot-Konto muss weg sein", !accountExists("Depot A"));
        assertTrue("Bewegungen des Depots müssen weg sein", transaktionen("Depot A", "S1").isEmpty());
    }

    @Test
    public void laesstAndereDepotsUnberuehrt() throws Exception {
        legeDepotAn("Depot A", "S1");
        legeDepotAn("Depot B", "S2");

        loescheUndWarte(Collections.emptyList(), Collections.singletonList("Depot A"));

        assertTrue("Depot A ist weg", !accountExists("Depot A"));
        assertTrue("Depot B bleibt bestehen", accountExists("Depot B"));
        assertEquals("Bewegungen von Depot B bleiben", 1, transaktionen("Depot B", "S2").size());
    }

    @Test
    public void loeschtKontenUndDepotsGemeinsam() throws Exception {
        legeDepotAn("Depot A", "S1");
        imHintergrund(() -> {
            db.accountDao().insertIfAbsent(new Account("Girokonto"));
            return null;
        });

        loescheUndWarte(Collections.singletonList("Girokonto"), Collections.singletonList("Depot A"));

        assertTrue(!accountExists("Girokonto"));
        assertTrue(!accountExists("Depot A"));
    }
}
