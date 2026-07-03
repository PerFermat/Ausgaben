package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookingDao {

    @Insert
    long insert(Booking booking);

    @Update
    void update(Booking booking);

    @Query("DELETE FROM booking WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM booking WHERE id = :id")
    Booking getById(long id);

    @Query("SELECT * FROM booking ORDER BY created_at DESC, id DESC")
    List<Booking> getAllBookings();

    @Query("SELECT * FROM booking WHERE exported = 0 ORDER BY created_at ASC, id ASC")
    List<Booking> getUnexported();

    @Query("UPDATE booking SET exported = 1 WHERE id IN (:ids)")
    void markExported(List<Long> ids);

    @Query("DELETE FROM booking")
    void deleteAll();

    /** Gesamtsaldo aller Buchungen (Einnahmen − Ausgaben). */
    @Query("SELECT COALESCE(SUM(CASE WHEN is_income THEN amount_cents ELSE -amount_cents END), 0) FROM booking")
    long getTotalBalance();

    /** Saldo eines einzelnen Kontos (Einnahmen − Ausgaben). */
    @Query("SELECT COALESCE(SUM(CASE WHEN is_income THEN amount_cents ELSE -amount_cents END), 0) "
            + "FROM booking WHERE account = :account")
    long getBalanceByAccount(String account);

    @Query("DELETE FROM booking WHERE account = :account AND exported = 1")
    void deleteExportedByAccount(String account);

    @Query("DELETE FROM booking WHERE account = :account")
    void deleteAllByAccount(String account);

    @Query("DELETE FROM booking WHERE transfer_group = :group")
    void deleteByTransferGroup(String group);

    /** Kategorien aus Einzelbuchungen UND Splitbuchungs-Teilen (für Filter/Baum/Auswertung). */
    @Query("SELECT DISTINCT category FROM ("
            + "SELECT category FROM booking WHERE category != '' "
            + "UNION SELECT category FROM booking_split WHERE category != '') "
            + "ORDER BY category COLLATE NOCASE ASC")
    List<String> getDistinctCategories();

    // ---- Splitbuchungs-Teile ----

    @Insert
    long insertSplit(BookingSplit split);

    @Query("SELECT * FROM booking_split WHERE booking_id = :bookingId ORDER BY id ASC")
    List<BookingSplit> getSplits(long bookingId);

    @Query("SELECT * FROM booking_split ORDER BY id ASC")
    List<BookingSplit> getAllSplits();

    @Query("DELETE FROM booking_split WHERE booking_id = :bookingId")
    void deleteSplits(long bookingId);

    @Query("DELETE FROM booking_split")
    void deleteAllSplits();

    @Query("DELETE FROM booking_split WHERE booking_id IN "
            + "(SELECT id FROM booking WHERE account = :account AND exported = 1)")
    void deleteSplitsForExportedAccount(String account);

    @Query("DELETE FROM booking_split WHERE booking_id IN "
            + "(SELECT id FROM booking WHERE account = :account)")
    void deleteSplitsForAccount(String account);
}
