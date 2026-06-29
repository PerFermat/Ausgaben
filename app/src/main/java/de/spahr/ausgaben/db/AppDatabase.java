package de.spahr.ausgaben.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Booking.class, Account.class, Payee.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** v1 → v2: Notiz-Spalte ergänzen (bestehende Buchungen bleiben erhalten). */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE booking ADD COLUMN note TEXT NOT NULL DEFAULT ''");
        }
    };

    public abstract BookingDao bookingDao();

    public abstract AccountDao accountDao();

    public abstract PayeeDao payeeDao();

    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "ausgaben.db")
                            .addMigrations(MIGRATION_1_2)
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
