package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Der Zuordnungsdialog schreibt erst bei „OK", und dann alles auf einmal: {@code selected} ist der
 * vollständige Sollzustand über alle eigenen Gruppen, dazu höchstens eine neu anzulegende.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AccountGroupMembershipTest {

    private AppDatabase db;
    private AccountGroupDao groupDao;
    private AccountGroupRepository repo;
    private ExecutorService executor;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).allowMainThreadQueries().build();
        groupDao = db.accountGroupDao();
        executor = Executors.newSingleThreadExecutor();
        repo = new AccountGroupRepository(db.accountDao(), groupDao, executor,
                new Handler(Looper.getMainLooper()));
        for (String name : new String[]{"Girokonto", "Bausparen", "Bargeld"}) {
            db.accountDao().insertIfAbsent(new Account(name));
        }
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
        db.close();
    }

    /** Legt eine eigene Gruppe mit einem Konto an und meldet ihre id. */
    private long eigeneGruppe(String name, String konto) {
        groupDao.insert(new AccountGroup(name, false));
        long id = groupDao.getIdByName(name);
        groupDao.addMember(new AccountGroupMember(id, db.accountDao().getIdByName(konto)));
        return id;
    }

    private Set<Long> auswahl(long... ids) {
        Set<Long> out = new HashSet<>();
        for (long id : ids) {
            out.add(id);
        }
        return out;
    }

    @Test
    public void hakenSetzenUndWegnehmenInEinemDurchgang() {
        long urlaub = eigeneGruppe("Urlaub", "Bargeld");
        long sparen = eigeneGruppe("Sparen", "Bausparen");

        // Girokonto kommt zu „Urlaub", „Sparen" bleibt ohne es – beides in einem Aufruf.
        assertEquals(AccountGroupRepository.APPLY_OK,
                repo.applyMembershipNow("Girokonto", auswahl(urlaub), ""));

        assertEquals(Arrays.asList("Bargeld", "Girokonto"), db.accountDao().getNamesInGroup(urlaub));
        assertEquals(Collections.singletonList("Bausparen"), db.accountDao().getNamesInGroup(sparen));
    }

    @Test
    public void abgewaehlteGruppeVerliertDasKontoUndVerschwindetLeer() {
        long urlaub = eigeneGruppe("Urlaub", "Bargeld");

        repo.applyMembershipNow("Bargeld", auswahl(), "");

        assertNull("ohne Mitglieder gibt es die Gruppe nicht mehr", groupDao.getIdByName("Urlaub"));
        assertTrue(db.accountDao().getNamesInGroup(urlaub).isEmpty());
    }

    @Test
    public void freiesFeldLegtDieNeueGruppeAn() {
        assertEquals(AccountGroupRepository.APPLY_OK,
                repo.applyMembershipNow("Girokonto", auswahl(), "  Urlaub  "));

        Long id = groupDao.getIdByName("Urlaub");
        assertNotNull(id);
        assertEquals(Collections.singletonList("Girokonto"), db.accountDao().getNamesInGroup(id));
    }

    @Test
    public void nameEinerDateiGruppeWirdAbgewiesenUndNichtsGeschrieben() {
        long urlaub = eigeneGruppe("Urlaub", "Bargeld");
        repo.applyFileGroups(null, Collections.singletonList("Bausparen"), "Favoriten");

        // „Favoriten" ist vergeben: der Aufruf bricht ab, bevor er den Haken bei „Urlaub" setzt.
        assertEquals(AccountGroupRepository.APPLY_NAME_FROM_FILE,
                repo.applyMembershipNow("Girokonto", auswahl(urlaub), "Favoriten"));

        assertEquals(Collections.singletonList("Bargeld"), db.accountDao().getNamesInGroup(urlaub));
    }

    @Test
    public void gruppenAusDerDateiBleibenUnberuehrt() {
        repo.applyFileGroups(null, Collections.singletonList("Girokonto"), "Favoriten");
        long favoriten = groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES).id;

        // Der Dialog kennt nur die eigenen Gruppen; eine leere Auswahl darf die Favoriten nicht räumen.
        repo.applyMembershipNow("Girokonto", auswahl(), "");

        assertEquals(Collections.singletonList("Girokonto"), db.accountDao().getNamesInGroup(favoriten));
    }
}
