package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Der Umzug der Bestandskategorien in die neue Tabelle (v47 → v48).
 *
 * <p>Auf den Geräten liegen echte Daten, und diese Migration ist die einzige Gelegenheit, sie
 * mitzunehmen: danach liest niemand mehr in {@code fee_category} und {@code income_category} nach.
 * Was hier verlorenginge, wäre ohne Sicherung nicht wiederzubekommen.</p>
 *
 * <p>Nachgestellt wird nur die eine Tabelle, um die es geht — Room ist dafür nicht nötig, die
 * Migration ist reines SQL.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxSplitMigrationTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    /** Die Tabelle {@code security_tx}, wie sie in Version 47 aussah, mit ein paar Zeilen darin. */
    private SupportSQLiteDatabase alterBestand() {
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(ctx)
                        .name(null)   // im Arbeitsspeicher
                        .callback(new SupportSQLiteOpenHelper.Callback(47) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE security_tx ("
                                        + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                                        + "action TEXT NOT NULL, "
                                        + "amount_cents INTEGER NOT NULL, "
                                        + "net_cents INTEGER NOT NULL, "
                                        + "fee_cents INTEGER NOT NULL, "
                                        + "fee_category TEXT NOT NULL, "
                                        + "income_category TEXT NOT NULL)");
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int from, int to) {
                            }
                        })
                        .build());
        SupportSQLiteDatabase db = helper.getWritableDatabase();
        // Eine Dividende: 906,99 brutto, 739,53 netto – die Steuer ist die Differenz.
        db.execSQL("INSERT INTO security_tx (id, action, amount_cents, net_cents, fee_cents, "
                + "fee_category, income_category) VALUES "
                + "(1, 'dividend', 90699, 73953, 0, 'Steuern', 'Dividenden')");
        // Ein Kauf: die Gebühr steht in fee_cents, eine Ertragskategorie gibt es dort nicht.
        db.execSQL("INSERT INTO security_tx (id, action, amount_cents, net_cents, fee_cents, "
                + "fee_category, income_category) VALUES "
                + "(2, 'buy', 25000, 25000, 490, 'Bankgebühren', '')");
        // Eine importierte Bewegung ganz ohne Kategorien.
        db.execSQL("INSERT INTO security_tx (id, action, amount_cents, net_cents, fee_cents, "
                + "fee_category, income_category) VALUES "
                + "(3, 'sell', 12000, 12000, 0, '', '')");
        return db;
    }

    private static long einzelwert(SupportSQLiteDatabase db, String sql) {
        try (Cursor c = db.query(sql)) {
            assertTrue("keine Zeile für: " + sql, c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static String text(SupportSQLiteDatabase db, String sql) {
        try (Cursor c = db.query(sql)) {
            assertTrue("keine Zeile für: " + sql, c.moveToFirst());
            return c.getString(0);
        }
    }

    @Test
    public void derBestandZiehtInDieNeueTabelleUm() {
        SupportSQLiteDatabase db = alterBestand();
        AppDatabase.MIGRATION_47_48.migrate(db);

        assertEquals("drei Kategorien waren gepflegt", 3,
                einzelwert(db, "SELECT COUNT(*) FROM security_tx_split"));

        // Bei der Dividende ist die Steuer die Differenz von Brutto und Netto.
        assertEquals(90699 - 73953, einzelwert(db,
                "SELECT amount_cents FROM security_tx_split WHERE tx_id = 1 AND income = 0"));
        assertEquals("Steuern", text(db,
                "SELECT category FROM security_tx_split WHERE tx_id = 1 AND income = 0"));
        // Der Ertrag hängt am Bruttobetrag.
        assertEquals(90699, einzelwert(db,
                "SELECT amount_cents FROM security_tx_split WHERE tx_id = 1 AND income = 1"));

        // Beim Kauf steht die Gebühr in ihrer eigenen Spalte.
        assertEquals(490, einzelwert(db,
                "SELECT amount_cents FROM security_tx_split WHERE tx_id = 2"));
        assertEquals("Bankgebühren", text(db,
                "SELECT category FROM security_tx_split WHERE tx_id = 2"));

        // Ohne gepflegte Kategorie entsteht keine Zeile – eine leere wäre schlimmer als keine.
        assertEquals(0, einzelwert(db,
                "SELECT COUNT(*) FROM security_tx_split WHERE tx_id = 3"));
    }

    /**
     * Danach sind die alten Spalten leer. Sie bleiben nur deshalb stehen, weil SQLite sie ohne einen
     * vollständigen Neuaufbau der Tabelle nicht hergibt; gelesen wird nirgends mehr in ihnen, und ein
     * doppelt geführter Wert wäre genau der, der eines Tages auseinanderläuft.
     */
    @Test
    public void dieAltenSpaltenBleibenLeerZurueck() {
        SupportSQLiteDatabase db = alterBestand();
        AppDatabase.MIGRATION_47_48.migrate(db);
        assertEquals(0, einzelwert(db, "SELECT COUNT(*) FROM security_tx "
                + "WHERE fee_category <> '' OR income_category <> ''"));
    }

    /** Zweimal angewandt entstünden Dubletten – deshalb darf die Migration nur einmal laufen. */
    @Test
    public void jedeZeileZiehtGenauEinmalUm() {
        SupportSQLiteDatabase db = alterBestand();
        AppDatabase.MIGRATION_47_48.migrate(db);
        AppDatabase.MIGRATION_47_48.migrate(db);
        assertEquals("die geleerten Spalten schützen vor einem zweiten Durchgang", 3,
                einzelwert(db, "SELECT COUNT(*) FROM security_tx_split"));
    }
}
