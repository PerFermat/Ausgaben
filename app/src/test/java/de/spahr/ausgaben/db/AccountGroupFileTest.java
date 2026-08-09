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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Die aus der KMyMoney-Datei abgeleiteten Kontengruppen: Banken aus dem Institutsblock und „Favoriten"
 * aus den bevorzugten Konten. Beide werden in einem Durchgang gesetzt – ein zweiter Aufruf würde löschen,
 * was der erste gerade geschrieben hat.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AccountGroupFileTest {

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
        for (String name : new String[]{"Girokonto", "Bausparen", "Bargeld", "ETF Depot"}) {
            db.accountDao().insertIfAbsent(new Account(name));
        }
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
        db.close();
    }

    private Map<String, String> institute(String... paare) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < paare.length; i += 2) {
            map.put(paare[i], paare[i + 1]);
        }
        return map;
    }

    private List<String> namen(List<AccountGroup> gruppen) {
        List<String> out = new ArrayList<>();
        for (AccountGroup g : gruppen) {
            out.add(g.name);
        }
        return out;
    }

    @Test
    public void favoritenEntstehenAusDerDatei() {
        repo.applyFileGroups(institute("Girokonto", "Volksbank"),
                Arrays.asList("Girokonto", "ETF Depot"), "Favoriten");

        AccountGroup favoriten = groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES);
        assertNotNull(favoriten);
        assertEquals("Favoriten", favoriten.name);
        assertTrue("aus der Datei abgeleitet, also unveränderlich", favoriten.auto);
        assertEquals(2, db.accountDao().getNamesInGroup(favoriten.id).size());
    }

    @Test
    public void favoritenStehenGanzOben() {
        db.accountDao().insertIfAbsent(new Account("Extra"));
        groupDao.insert(new AccountGroup("Aachener", false));
        groupDao.addMember(new AccountGroupMember(
                groupDao.getIdByName("Aachener"), db.accountDao().getIdByName("Extra")));
        repo.applyFileGroups(institute("Girokonto", "Volksbank"),
                Arrays.asList("Girokonto"), "Favoriten");

        // Trotz „A" vor „F" vor „V": die Favoriten kommen zuerst, dann die eigene, dann die Bank.
        assertEquals(Arrays.asList("Favoriten", "Aachener", "Volksbank"), namen(groupDao.getSelectable()));
    }

    @Test
    public void bankenUndFavoritenUeberlebenDenselbenDurchgang() {
        repo.applyFileGroups(institute("Girokonto", "Volksbank", "Bausparen", "LBS"),
                Arrays.asList("Bargeld"), "Favoriten");

        assertEquals(1, db.accountDao().getNamesInGroup(groupDao.getIdByName("Volksbank")).size());
        assertEquals(1, db.accountDao().getNamesInGroup(groupDao.getIdByName("LBS")).size());
        assertEquals(1, db.accountDao().getNamesInGroup(
                groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES).id).size());
    }

    @Test
    public void ohneBevorzugteKontenVerschwindetDieGruppe() {
        repo.applyFileGroups(institute("Girokonto", "Volksbank"), Arrays.asList("Girokonto"), "Favoriten");
        assertNotNull(groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES));

        repo.applyFileGroups(institute("Girokonto", "Volksbank"), new ArrayList<>(), "Favoriten");

        assertNull(groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES));
        assertNotNull("die Bankgruppe bleibt vollständig", groupDao.getIdByName("Volksbank"));
    }

    @Test
    public void eigeneGleichnamigeGruppeWeicht() {
        groupDao.insert(new AccountGroup("Favoriten", false));
        long eigene = groupDao.getIdByName("Favoriten");
        groupDao.addMember(new AccountGroupMember(eigene, db.accountDao().getIdByName("Bargeld")));

        repo.applyFileGroups(null, Arrays.asList("Girokonto"), "Favoriten");

        AccountGroup favoriten = groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES);
        assertNotNull(favoriten);
        assertEquals("Favoriten", favoriten.name); // ohne Zusatz – die eigene Gruppe ist gewichen
        assertEquals(Arrays.asList("Girokonto"), db.accountDao().getNamesInGroup(favoriten.id));
        assertEquals(1, groupDao.getAll().size());
    }

    @Test
    public void nameFolgtDerSprache() {
        repo.applyFileGroups(null, Arrays.asList("Girokonto"), "Favoriten");
        long id = groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES).id;

        repo.renameFavorites("Favourites");

        assertEquals("Favourites", groupDao.getById(id).name);
        // Die Zuordnung bleibt vom Umbenennen unberührt.
        assertEquals(Arrays.asList("Girokonto"), db.accountDao().getNamesInGroup(id));
    }

    @Test
    public void umbenennenRaeumtEineGleichnamigeEigeneGruppeWeg() {
        repo.applyFileGroups(null, Arrays.asList("Girokonto"), "Favoriten");
        groupDao.insert(new AccountGroup("Favourites", false));

        repo.renameFavorites("Favourites");

        assertEquals("Favourites",
                groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES).name);
        assertEquals(1, groupDao.getAll().size());
    }
}
