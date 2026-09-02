package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Die Migration 49→50 rechnet {@code net_cents} für Käufe und Verkäufe nach.
 *
 * <p>Dort stand bis dahin noch einmal der Bruttobetrag. Bei jeder Bewegung mit Gebühr wich das von der
 * Geldbuchung ab, und {@code SecurityTxMatch} — das genau dieses Feld gegen den Buchungsbetrag
 * vergleicht — fand die Bewegung nicht mehr; eine gelöschte Buchung ließ ihre Bewegung stehen.</p>
 *
 * <p>Geprüft wird die Rechnung selbst, auf einer nachgebauten Tabelle: Room aufzusetzen und über 49
 * Versionen zu migrieren, brauchte den vollen Migrationstest-Apparat und prüfte am Ende dieselben zwei
 * {@code UPDATE}-Anweisungen. Was diese Prüfung <b>nicht</b> abdeckt, ist die Verdrahtung — dass die
 * Migration in {@code addMigrations} steht und die Datenbankversion erhöht wurde.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NetCentsMigrationTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();
    private File datei;
    private SQLiteDatabase db;

    @Before
    public void tabelleAufbauen() throws Exception {
        datei = File.createTempFile("migration", ".db", ctx.getCacheDir());
        db = SQLiteDatabase.openOrCreateDatabase(datei, null);
        db.execSQL("CREATE TABLE security_tx (id INTEGER PRIMARY KEY, action TEXT NOT NULL, "
                + "amount_cents INTEGER NOT NULL, net_cents INTEGER NOT NULL, "
                + "fee_cents INTEGER NOT NULL)");
    }

    @After
    public void aufraeumen() {
        db.close();
        //noinspection ResultOfMethodCallIgnored
        datei.delete();
    }

    /** Eine Zeile im Zustand vor der Migration: net_cents ist eine Kopie von amount_cents. */
    private void alteZeile(long id, String action, long brutto, long gebuehr) {
        db.execSQL("INSERT INTO security_tx VALUES (?, ?, ?, ?, ?)",
                new Object[]{id, action, brutto, brutto, gebuehr});
    }

    /** Genau die beiden Anweisungen der Migration. */
    private void migriere() {
        db.execSQL("UPDATE security_tx SET net_cents = amount_cents - ABS(fee_cents) "
                + "WHERE action = 'sell'");
        db.execSQL("UPDATE security_tx SET net_cents = amount_cents + ABS(fee_cents) "
                + "WHERE action NOT IN ('sell', 'dividend')");
    }

    private long netVon(long id) {
        try (Cursor c = db.rawQuery("SELECT net_cents FROM security_tx WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    @Test
    public void beimVerkaufGehtDieGebuehrAbBeimKaufKommtSieHinzu() {
        alteZeile(1, "buy", 100_000L, 490L);
        alteZeile(2, "sell", 100_000L, 490L);

        migriere();

        assertEquals("Kauf: bezahlt wird Brutto plus Gebühr", 100_490L, netVon(1));
        assertEquals("Verkauf: gutgeschrieben wird Brutto minus Gebühr", 99_510L, netVon(2));
    }

    /** Dividenden bleiben unberührt – dort war das Feld schon immer die Gutschrift. */
    @Test
    public void dividendenWerdenNichtAngefasst() {
        db.execSQL("INSERT INTO security_tx VALUES (3, 'dividend', 10000, 7400, 0)");

        migriere();

        assertEquals(7_400L, netVon(3));
    }

    /** Ein-/Ausbuchungen bewegen kein Geld; die Rechnung liefert dort von selbst 0. */
    @Test
    public void einUndAusbuchungenBleibenBeiNull() {
        alteZeile(4, "add", 0L, 0L);
        alteZeile(5, "remove", 0L, 0L);

        migriere();

        assertEquals(0L, netVon(4));
        assertEquals(0L, netVon(5));
    }

    /**
     * Zweimal migrieren darf nicht zweimal rechnen. Room führt eine Migration zwar nur einmal aus —
     * aber wenn sie es nicht wäre, verschöbe sich der Betrag bei jedem Durchlauf weiter, und niemand
     * sähe es der Zahl an.
     */
    @Test
    public void einZweiterDurchlaufVerschiebtNichtsWeiter() {
        alteZeile(6, "buy", 100_000L, 490L);

        migriere();
        long nachEinmal = netVon(6);
        migriere();

        assertEquals(nachEinmal, netVon(6));
    }

    /** Und ohne Gebühr ändert sich nichts – der häufigste Fall bleibt, wie er war. */
    @Test
    public void ohneGebuehrBleibtAllesWieEsWar() {
        alteZeile(7, "buy", 100_000L, 0L);
        alteZeile(8, "sell", 100_000L, 0L);

        migriere();

        assertEquals(100_000L, netVon(7));
        assertEquals(100_000L, netVon(8));
    }
}
