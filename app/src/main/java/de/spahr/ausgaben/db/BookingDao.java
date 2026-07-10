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

    /** Zuletzt angelegte Buchung, deren Empfänger den Suchbegriff enthält (für die Sprach-Schnellerfassung). */
    @Query("SELECT * FROM booking WHERE payee LIKE '%' || :term || '%' COLLATE NOCASE "
            + "ORDER BY created_at DESC, id DESC LIMIT 1")
    Booking findLatestByPayeeLike(String term);

    /** Alle Buchungen, deren Empfänger den Suchbegriff enthält (neueste zuerst) – für die Nächster-Auswahl. */
    @Query("SELECT * FROM booking WHERE payee LIKE '%' || :term || '%' COLLATE NOCASE "
            + "ORDER BY created_at DESC, id DESC")
    List<Booking> findByPayeeLike(String term);

    /** Vorhandene Empfängernamen (je einmal), neueste Buchung zuerst – für die unscharfe Sprachsuche. */
    @Query("SELECT payee FROM booking WHERE payee != '' GROUP BY payee ORDER BY MAX(created_at) DESC")
    List<String> getDistinctPayees();

    @Query("SELECT * FROM booking ORDER BY created_at DESC, id DESC")
    List<Booking> getAllBookings();

    /** Die letzten Buchungen (für das Homescreen-Widget). */
    @Query("SELECT * FROM booking ORDER BY created_at DESC, id DESC LIMIT :limit")
    List<Booking> getRecent(int limit);

    /** Buchungen mit Standort in der Notiz (neueste zuerst) – Vorlagen für die Betrag-only-Erfassung. */
    @Query("SELECT * FROM booking WHERE note LIKE '%GPS:%' ORDER BY created_at DESC, id DESC LIMIT 500")
    List<Booking> getWithGpsNote();

    /** Ort-Link an allen Buchungen eines Kontos umbenennen (folgt dem Umbenennen in der Ortsverwaltung). */
    @Query("UPDATE booking SET place = :newName WHERE account = :account AND place = :oldName")
    void renamePlace(String account, String oldName, String newName);

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

    /** Kontosaldo bis einschließlich dieser Buchung (nach created_at, bei Gleichstand nach id). */
    @Query("SELECT COALESCE(SUM(CASE WHEN is_income THEN amount_cents ELSE -amount_cents END), 0) "
            + "FROM booking WHERE account = :account "
            + "AND (created_at < :createdAt OR (created_at = :createdAt AND id <= :id))")
    long getBalanceUpTo(String account, long createdAt, long id);

    @Query("DELETE FROM booking WHERE account = :account AND exported = 1")
    void deleteExportedByAccount(String account);

    @Query("DELETE FROM booking WHERE account = :account")
    void deleteAllByAccount(String account);

    @Query("DELETE FROM booking WHERE transfer_group = :group")
    void deleteByTransferGroup(String group);

    @Query("SELECT * FROM booking WHERE transfer_group = :group")
    List<Booking> getByTransferGroup(String group);

    /** Kategorien aus Einzelbuchungen UND Splitbuchungs-Teilen (für Filter/Baum/Auswertung). */
    @Query("SELECT DISTINCT category FROM ("
            + "SELECT category FROM booking WHERE category != '' "
            + "UNION SELECT category FROM booking_split WHERE category != '') "
            + "ORDER BY category COLLATE NOCASE ASC")
    List<String> getDistinctCategories();

    /** Einnahme-Kategorien (Buchungen mit is_income=1; Split-Teile über die zugehörige Buchung). */
    @Query("SELECT DISTINCT category FROM ("
            + "SELECT category FROM booking WHERE category != '' AND is_income = 1 "
            + "UNION SELECT bs.category FROM booking_split bs JOIN booking b ON bs.booking_id = b.id "
            + "WHERE bs.category != '' AND b.is_income = 1) "
            + "ORDER BY category COLLATE NOCASE ASC")
    List<String> getIncomeCategories();

    /** Ausgabe-Kategorien (Buchungen mit is_income=0; Split-Teile über die zugehörige Buchung). */
    @Query("SELECT DISTINCT category FROM ("
            + "SELECT category FROM booking WHERE category != '' AND is_income = 0 "
            + "UNION SELECT bs.category FROM booking_split bs JOIN booking b ON bs.booking_id = b.id "
            + "WHERE bs.category != '' AND b.is_income = 0) "
            + "ORDER BY category COLLATE NOCASE ASC")
    List<String> getExpenseCategories();

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
