package de.spahr.ausgaben.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Booking.class, BookingSplit.class, Account.class, Payee.class, PlaceEntry.class,
        PayeeCorrection.class, Translation.class, Language.class},
        version = 13, exportSchema = false)
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

    public abstract BookingDao bookingDao();

    public abstract AccountDao accountDao();

    public abstract PayeeDao payeeDao();

    public abstract PlaceEntryDao placeEntryDao();

    public abstract PayeeCorrectionDao payeeCorrectionDao();

    public abstract TranslationDao translationDao();

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
                                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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
