package de.spahr.ausgaben.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Booking.class, BookingSplit.class, Account.class, Payee.class, PlaceEntry.class},
        version = 6, exportSchema = false)
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

    public abstract BookingDao bookingDao();

    public abstract AccountDao accountDao();

    public abstract PayeeDao payeeDao();

    public abstract PlaceEntryDao placeEntryDao();

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
                                    MIGRATION_5_6)
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
