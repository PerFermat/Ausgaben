package de.spahr.ausgaben.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Booking.class, BookingSplit.class, Account.class, Payee.class, PlaceEntry.class,
        PayeeCorrection.class, Translation.class, Language.class, Security.class, SecurityTx.class,
        Budget.class, CategoryType.class},
        version = 24, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** v1 → v2: Notiz-Spalte ergänzen (bestehende Buchungen bleiben erhalten). */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE booking ADD COLUMN note TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v2 → v3: Kategorie-Spalte ergänzen. */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE booking ADD COLUMN category TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v3 → v4: Orts-Bewegungsjournal (Bargeld-Orte) anlegen. */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS place_entry ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "place TEXT NOT NULL, "
                    + "amount_cents INTEGER NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "type TEXT NOT NULL)");
        }
    };

    /** v4 → v5: Orte an Konten binden – Konto-Spalte im Orts-Journal ergänzen. */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE place_entry ADD COLUMN account TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v5 → v6: Splitbuchungen (booking_split) + Umbuchungs-Felder an der Buchung. */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS booking_split ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "booking_id INTEGER NOT NULL, "
                    + "category TEXT NOT NULL, "
                    + "amount_cents INTEGER NOT NULL)");
            db.execSQL("ALTER TABLE booking ADD COLUMN is_transfer INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE booking ADD COLUMN transfer_account TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE booking ADD COLUMN transfer_group TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v6 → v7: gelernte Namenskorrekturen für die Spracherkennung. */
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS payee_correction ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "spoken TEXT NOT NULL, "
                    + "corrected TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payee_correction_spoken "
                    + "ON payee_correction(spoken)");
        }
    };

    /** v7 → v8: Alias-Felder (Konto, Kategorien, Von/Bis-Konto) an der Korrektur-/Alias-Tabelle. */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN account TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN cat_income_1 TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN cat_income_2 TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN cat_expense_1 TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN cat_expense_2 TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN from_account TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN to_account TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v8 → v9: „preferred"-Kennzeichen (Alias vor bestehender Buchung berücksichtigen). */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN preferred INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** v9 → v10: bevorzugte Buchungsart des Alias. */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN type TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v10 → v11: Übersetzungs-Tabellen für die Mehrsprachigkeit. */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS translation ("
                    + "lang TEXT NOT NULL, "
                    + "key TEXT NOT NULL, "
                    + "value TEXT NOT NULL, "
                    + "PRIMARY KEY(lang, key))");
            db.execSQL("CREATE TABLE IF NOT EXISTS language ("
                    + "code TEXT NOT NULL PRIMARY KEY, "
                    + "name TEXT NOT NULL)");
        }
    };

    /** v11 → v12: Währungskennzeichen je Konto. */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE account ADD COLUMN currency TEXT NOT NULL DEFAULT ''");
        }
    };

    /** v12 → v13: Standort (lat/lon) am Alias für die Betrag-only-Erfassung per GPS. */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN lat REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN lon REAL NOT NULL DEFAULT 0");
        }
    };

    /** v13 → v14: Ort an der Buchung; Orts-Bewegungsjournal auf 0 zurücksetzen (Salden aus Buchungen). */
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE booking ADD COLUMN place TEXT NOT NULL DEFAULT ''");
            db.execSQL("DELETE FROM place_entry");
        }
    };

    /**
     * v14 → v15: Ort-Bewegungsjournal ist wieder die Saldo-Quelle. {@code place_entry.note} für die
     * Bewegungsbeschreibung; {@code booking.place_managed} kennzeichnet in der App angelegte Buchungen
     * mit Ort-Verknüpfung (importierte bleiben 0). {@code booking.place} bleibt als loser Link erhalten.
     */
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE place_entry ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE booking ADD COLUMN place_managed INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** v15 → v16: Konten können geschlossen (inaktiv) werden. */
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE account ADD COLUMN closed INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** v16 → v17: Depot-Import – Wertpapiere (mit letztem Kurs) und ihre Bewegungen. */
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS security ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "depot TEXT NOT NULL, kmy_id TEXT NOT NULL, name TEXT NOT NULL, "
                    + "symbol TEXT NOT NULL, currency TEXT NOT NULL, "
                    + "price REAL NOT NULL, price_date INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_security_depot_kmy_id "
                    + "ON security(depot, kmy_id)");
            db.execSQL("CREATE TABLE IF NOT EXISTS security_tx ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "depot TEXT NOT NULL, security_kmy_id TEXT NOT NULL, security_name TEXT NOT NULL, "
                    + "date INTEGER NOT NULL, action TEXT NOT NULL, shares REAL NOT NULL, "
                    + "amount_cents INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_security_tx_depot ON security_tx(depot)");
        }
    };

    // Alias-Unique von spoken → (spoken, corrected): gleicher gesprochener Begriff darf auf mehrere
    // Empfänger zeigen (per GPS unterschieden). Bestehende Daten sind eindeutig, daher unproblematisch.
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP INDEX IF EXISTS index_payee_correction_spoken");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payee_correction_spoken_corrected "
                    + "ON payee_correction(spoken, corrected)");
        }
    };

    // Konto-Typ (KMyMoney) für die Trennung in Anlage-/Verbindlichkeitskonten. 0 = unbekannt/Anlage.
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE account ADD COLUMN acct_type INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Namenloses Konto entfernen; Orte im Alias (Ausgabe/Einnahme + Umbuchung Von/Nach).
    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DELETE FROM account WHERE TRIM(name) = ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN place TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN from_place TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN to_place TEXT NOT NULL DEFAULT ''");
        }
    };

    // Netto-Betrag je Depot-Bewegung (für Dividenden brutto/netto). Vorbelegt = amount_cents; echter
    // Netto-Wert kommt beim nächsten Depot-Import.
    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE security_tx ADD COLUMN net_cents INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE security_tx SET net_cents = amount_cents");
        }
    };

    /** Alias-Standorte: von einer Koordinate (lat/lon) auf eine Koordinatenliste (gps_list) erweitern. */
    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE payee_correction ADD COLUMN gps_list TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE payee_correction SET gps_list = lat || ',' || lon "
                    + "WHERE lat != 0 OR lon != 0");
        }
    };

    /** Budgetplanung: Soll-Werte je Kategorie und Jahr. */
    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS budget ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "year INTEGER NOT NULL, "
                    + "category TEXT NOT NULL, "
                    + "is_income INTEGER NOT NULL, "
                    + "amount_cents INTEGER NOT NULL, "
                    + "source TEXT NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budget_year_category_is_income "
                    + "ON budget(year, category, is_income)");
        }
    };

    /** Kategorie-Typ (Einnahme/Ausgabe) aus der KMyMoney-Datei – verlässliche Budget-Einordnung. */
    static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS category_type ("
                    + "category TEXT PRIMARY KEY NOT NULL, "
                    + "is_income INTEGER NOT NULL)");
        }
    };

    public abstract BookingDao bookingDao();

    public abstract AccountDao accountDao();

    public abstract PayeeDao payeeDao();

    public abstract PlaceEntryDao placeEntryDao();

    public abstract PayeeCorrectionDao payeeCorrectionDao();

    public abstract TranslationDao translationDao();

    public abstract SecurityDao securityDao();

    public abstract BudgetDao budgetDao();

    public abstract CategoryTypeDao categoryTypeDao();

    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "ausgaben.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                                    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
                                    MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                                    MIGRATION_22_23, MIGRATION_23_24)
                            .build();
                }
            }
        }
        return instance;
    }

    /** Schließt die offene Datenbank und verwirft die Singleton-Instanz (für Restore). */
    public static void closeInstance() {
        synchronized (AppDatabase.class) {
            if (instance != null) {
                if (instance.isOpen()) {
                    instance.close();
                }
                instance = null;
            }
        }
    }
}
