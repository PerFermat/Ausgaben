package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Grundverhalten des Multi-Profil-Umbaus: Migration einer Bestandsinstallation, Anlegen/Wechseln/
 * Löschen von Profilen, und dass die Datenquelle in {@link SettingsStore} je Profil getrennt ist.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProfileManagerTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    /**
     * Robolectric legt SharedPreferences/Datenbankdateien dateibasiert ab; ohne expliziten Reset könnte
     * ein früherer Testfall in derselben Klasse ein Profil hinterlassen, das
     * {@code migrateLegacyInstallationIfNeeded} dann als „schon migriert" ansieht.
     */
    @Before
    public void resetProfileState() {
        ctx.getSharedPreferences("ausgaben_profiles", Context.MODE_PRIVATE).edit().clear().commit();
        ctx.getSharedPreferences("ausgaben_settings", Context.MODE_PRIVATE).edit().clear().commit();
        ctx.getSharedPreferences("ausgaben_places", Context.MODE_PRIVATE).edit().clear().commit();
        ctx.getSharedPreferences("ausgaben_statements", Context.MODE_PRIVATE).edit().clear().commit();
        SettingsStore.secretPrefs(ctx).edit().clear().commit();
        ctx.getDatabasePath("ausgaben.db").delete();
    }

    @Test
    public void neuinstallationBekommtEinProfil() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);

        ProfileManager pm = new ProfileManager(ctx);
        List<ProfileManager.Profile> profiles = pm.getProfiles();
        assertEquals(1, profiles.size());
        assertEquals(profiles.get(0).id, pm.getActiveProfileId());
    }

    @Test
    public void migrationIstIdempotent() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        String firstActive = new ProfileManager(ctx).getActiveProfileId();

        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);

        ProfileManager pm = new ProfileManager(ctx);
        assertEquals(1, pm.getProfiles().size());
        assertEquals(firstActive, pm.getActiveProfileId());
    }

    @Test
    public void bestandsinstallationBekommtLegacyProfilAufVorhandenerDatei() {
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                ctx.getDatabasePath("ausgaben.db"), null).close();
        assertTrue(ctx.getDatabasePath("ausgaben.db").exists());

        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);

        ProfileManager pm = new ProfileManager(ctx);
        assertEquals(ProfileManager.LEGACY_PROFILE_ID, pm.getActiveProfileId());
        assertEquals("ausgaben.db", pm.getActiveProfile().dbFileName);
    }

    /**
     * Regression: Das Server-Passwort lag bis 1.11 unter dem unpräfixierten Schlüssel im
     * verschlüsselten Speicher, wird ab 1.12 aber profilweise gelesen. Ohne den Umzug in
     * {@code copyLegacySettingsUnderPrefix} verlor jede Bestandsinstallation mit Serveranbindung beim
     * Update ihre Zugangsdaten – ohne Hinweis auf die Ursache.
     */
    @Test
    public void bestandsinstallationBehaeltServerPasswort() {
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                ctx.getDatabasePath("ausgaben.db"), null).close();
        SettingsStore.secretPrefs(ctx).edit()
                .putString("nextcloud_password", "geheim").commit();

        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);

        assertEquals("geheim", new SettingsStore(ctx).getPassword());
        assertTrue(new SettingsStore(ctx).hasPassword());
        // Der alte Schlüssel ist geräumt, damit ein später angelegtes Profil ihn nicht mehr erbt.
        assertFalse(SettingsStore.secretPrefs(ctx).contains("nextcloud_password"));
    }

    /** Ein danach angelegtes zweites Profil startet ohne die Zugangsdaten des ersten. */
    @Test
    public void zweitesProfilErbtDasPasswortNicht() {
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                ctx.getDatabasePath("ausgaben.db"), null).close();
        SettingsStore.secretPrefs(ctx).edit()
                .putString("nextcloud_password", "geheim").commit();
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);

        ProfileManager pm = new ProfileManager(ctx);
        pm.switchTo(ctx, pm.createProfile("Zweitprofil").id);

        assertTrue(new SettingsStore(ctx).getPassword().isEmpty());
    }

    /**
     * {@code clearAll} löschte die Datenbankdateien, bevor es die Verbindung schloss. SQLite hielt die
     * Datei des aktiven Profils da noch offen; das anschließende {@code close()} schrieb den WAL-Puffer
     * zurück, sodass der „Werksreset" je nach Zeitpunkt eine Rest-Datenbank hinterließ.
     *
     * <p><b>Achtung:</b> Dieser Test läuft auch mit der alten Reihenfolge grün — Robolectrics
     * SQLite-Nachbau zeigt das Verhalten von POSIX-{@code unlink} auf einer offenen Datei nicht. Er
     * sichert also die Nachbedingung (nach dem Reset liegt nichts mehr herum), nicht die Reihenfolge
     * selbst; die steht als Kommentar in {@code clearAll}. Belegen ließe sich der Fehler nur auf einem
     * echten Gerät.</p>
     */
    @Test
    public void werksresetLaesstKeineDatenbankdateienZurueck() throws Exception {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String dbFile = pm.getActiveProfile().dbFileName;
        // Room öffnet die Datei erst beim ersten Zugriff – und der muss abseits des Hauptfadens laufen.
        // Ohne offene Verbindung ginge der Fehler gerade nicht auf.
        java.util.concurrent.ExecutorService io =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            io.submit(() -> de.spahr.ausgaben.db.AppDatabase.getInstance(ctx).accountDao()
                    .insertIfAbsent(new de.spahr.ausgaben.db.Account("Girokonto")))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            io.shutdownNow();
        }
        assertTrue("Voraussetzung: die Datenbank liegt auf der Platte",
                ctx.getDatabasePath(dbFile).exists());

        pm.clearAll(ctx);

        assertFalse("die Datenbank selbst", ctx.getDatabasePath(dbFile).exists());
        assertFalse("der WAL-Puffer", ctx.getDatabasePath(dbFile + "-wal").exists());
        assertFalse("das Shared-Memory-Abbild", ctx.getDatabasePath(dbFile + "-shm").exists());
        assertTrue("und die Profilliste ist leer", new ProfileManager(ctx).getProfiles().isEmpty());
    }

    @Test
    public void neuesProfilBekommtEigenenDateinamen() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstDb = pm.getActiveProfile().dbFileName;

        ProfileManager.Profile second = pm.createProfile("Zweitprofil");

        assertNotEquals(firstDb, second.dbFileName);
        assertEquals(2, pm.getProfiles().size());
    }

    @Test
    public void switchToWechseltAktivesProfil() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        ProfileManager.Profile second = pm.createProfile("Zweitprofil");

        pm.switchTo(ctx, second.id);

        assertEquals(second.id, pm.getActiveProfileId());
    }

    /**
     * Regression: In einem frisch angelegten, leeren Profil fehlte die Sprachauswahl in den
     * Einstellungen, weil Deutsch/Englisch als Zeilen in der (profileigenen) Datenbank stehen und nur
     * beim App-Kaltstart in die damals aktive Datenbank gesät wurden – nicht in ein danach neu
     * angelegtes Profil.
     */
    @Test
    public void neuesProfilHatSprachenZurAuswahl() throws InterruptedException {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        de.spahr.ausgaben.i18n.LocaleManager.init(ctx); // wie AusgabenApp.onCreate() beim Kaltstart
        ProfileManager pm = new ProfileManager(ctx);
        ProfileManager.Profile second = pm.createProfile("Zweitprofil");

        pm.switchTo(ctx, second.id);

        final List<de.spahr.ausgaben.db.Language>[] result = new List[1];
        java.util.concurrent.CountDownLatch fertig = new java.util.concurrent.CountDownLatch(1);
        new de.spahr.ausgaben.db.Repository(ctx).getLanguages(list -> {
            result[0] = list;
            fertig.countDown();
        });
        for (int i = 0; i < 50 && fertig.getCount() > 0; i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            fertig.await(20, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        assertTrue(result[0] != null && result[0].size() >= 2);
    }

    @Test
    public void datenquelleIstJeProfilGetrennt() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstId = pm.getActiveProfileId();
        new SettingsStore(ctx).setUrl("https://erstes-profil.example/");

        ProfileManager.Profile second = pm.createProfile("Zweitprofil");
        pm.switchTo(ctx, second.id);
        assertTrue(new SettingsStore(ctx).getUrl().isEmpty());
        new SettingsStore(ctx).setUrl("https://zweites-profil.example/");

        pm.switchTo(ctx, firstId);
        assertEquals("https://erstes-profil.example/", new SettingsStore(ctx).getUrl());
    }

    @Test
    public void letztesProfilLaesstSichNichtLoeschen() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String onlyId = pm.getActiveProfileId();

        pm.deleteProfile(ctx, onlyId);

        assertEquals(1, pm.getProfiles().size());
        assertEquals(onlyId, pm.getActiveProfileId());
    }

    @Test
    public void loeschenDesAktivenProfilsWechseltVorherAufAnderes() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstId = pm.getActiveProfileId();
        ProfileManager.Profile second = pm.createProfile("Zweitprofil");
        pm.switchTo(ctx, second.id);

        pm.deleteProfile(ctx, second.id);

        assertEquals(1, pm.getProfiles().size());
        assertEquals(firstId, pm.getActiveProfileId());
    }

    @Test
    public void ortsdatenSindJeProfilGetrennt() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstId = pm.getActiveProfileId();
        new PlacesStore(ctx).addPlace("Girokonto", "Zuhause");

        ProfileManager.Profile second = pm.createProfile("Zweitprofil");
        pm.switchTo(ctx, second.id);
        assertTrue(new PlacesStore(ctx).getPlaces("Girokonto").isEmpty());
        new PlacesStore(ctx).addPlace("Girokonto", "Büro");

        pm.switchTo(ctx, firstId);
        assertEquals(java.util.Collections.singletonList("Zuhause"),
                new PlacesStore(ctx).getPlaces("Girokonto"));
    }

    /**
     * Regression: Ein Depot(-name) tauchte im falschen Profil auf, weil die gelernten
     * Erkennungsregeln/ISIN-Zuordnungen (StatementTemplates) in einer einzigen, nicht profilgebundenen
     * Prefs-Datei lagen.
     */
    @Test
    public void statementVorlagenSindJeProfilGetrennt() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstId = pm.getActiveProfileId();
        new StatementTemplates(ctx).rememberSecurity("DE0001234567", "Depot A", "kmy-1", "Wertpapier A");

        ProfileManager.Profile second = pm.createProfile("Zweitprofil");
        pm.switchTo(ctx, second.id);
        assertTrue(new StatementTemplates(ctx).security("DE0001234567") == null);
        new StatementTemplates(ctx).rememberSecurity("DE0001234567", "Depot B", "kmy-2", "Wertpapier B");

        pm.switchTo(ctx, firstId);
        String[] found = new StatementTemplates(ctx).security("DE0001234567");
        assertEquals("Depot A", found[0]);
    }

    @Test
    public void copySettingsFromUebernimmtDatenquelleFarbeUndOrte() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstId = pm.getActiveProfileId();
        new SettingsStore(ctx).setUrl("https://quelle.example/");
        new SettingsStore(ctx).setCurrency("$");
        pm.setAccentColor(firstId, 0xFFAA00AA);
        new PlacesStore(ctx).addPlace("Girokonto", "Zuhause");

        ProfileManager.Profile second = pm.createProfile("Zweitprofil");
        pm.switchTo(ctx, second.id);

        pm.copySettingsFrom(ctx, firstId, second.id);

        assertEquals("https://quelle.example/", new SettingsStore(ctx).getUrl());
        assertEquals("$", new SettingsStore(ctx).getCurrency());
        assertEquals(java.util.Collections.singletonList("Zuhause"),
                new PlacesStore(ctx).getPlaces("Girokonto"));
        for (ProfileManager.Profile p : pm.getProfiles()) {
            if (p.id.equals(second.id)) {
                assertEquals(0xFFAA00AA, p.accentColor);
            }
        }

        // Quellprofil bleibt unverändert.
        pm.switchTo(ctx, firstId);
        assertEquals("https://quelle.example/", new SettingsStore(ctx).getUrl());
    }

    /**
     * Standardkonto und Kontengruppe verweisen auf Zeilen der Quelldatenbank – im neuen, noch leeren
     * Zielprofil gäbe es dieses Konto nicht, das Feld wäre also falsch belegt und nicht auswählbar.
     */
    @Test
    public void copySettingsFromUebernimmtStandardkontoNicht() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        String firstId = pm.getActiveProfileId();
        SettingsStore firstSettings = new SettingsStore(ctx);
        firstSettings.setDefaultAccount("Girokonto");
        firstSettings.setAccountGroup(7L);

        ProfileManager.Profile second = pm.createProfile("Zweitprofil");
        pm.switchTo(ctx, second.id);

        pm.copySettingsFrom(ctx, firstId, second.id);

        assertEquals("", new SettingsStore(ctx).getDefaultAccount());
        assertEquals(0L, new SettingsStore(ctx).getAccountGroup());

        // Quellprofil behält sein Standardkonto.
        pm.switchTo(ctx, firstId);
        assertEquals("Girokonto", new SettingsStore(ctx).getDefaultAccount());
    }
}
