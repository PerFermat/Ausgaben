package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PlaceEntryDao {

    @Insert
    long insert(PlaceEntry entry);

    @Update
    void update(PlaceEntry entry);

    /** Löscht eine einzelne Ort-Bewegung (Journal-CRUD in der Ort-Ansicht). */
    @Query("DELETE FROM place_entry WHERE id = :id")
    void delete(long id);

    /** Saldo je Ort eines Kontos (Summe der Bewegungen). */
    @Query("SELECT place AS place, SUM(amount_cents) AS balance_cents FROM place_entry "
            + "WHERE account = :account GROUP BY place")
    List<PlaceBalance> getBalances(String account);

    /** Saldo je (Konto, Ort) über alle Konten – für die Bestände-Gruppenliste. */
    @Query("SELECT account AS account, place AS place, SUM(amount_cents) AS balance_cents "
            + "FROM place_entry GROUP BY account, place")
    List<PlaceBalance> getAllBalances();

    /** Aktueller Saldo eines einzelnen Ortes eines Kontos. */
    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM place_entry "
            + "WHERE account = :account AND place = :place")
    long getBalance(String account, String place);

    /** Bewegungen eines Ortes, chronologisch aufsteigend (für den Verlauf). */
    @Query("SELECT * FROM place_entry WHERE account = :account AND place = :place "
            + "ORDER BY created_at ASC, id ASC")
    List<PlaceEntry> getByPlace(String account, String place);

    /** Alle Bewegungen, chronologisch aufsteigend (für die Auswertung). */
    @Query("SELECT * FROM place_entry ORDER BY created_at ASC, id ASC")
    List<PlaceEntry> getAll();

    @Query("UPDATE place_entry SET place = :newName WHERE account = :account AND place = :oldName")
    void renamePlace(String account, String oldName, String newName);

    @Query("DELETE FROM place_entry WHERE account = :account AND place = :place")
    void deleteByPlace(String account, String place);

    /** Ordnet noch nicht zugeordnete Bewegungen (account = '') einmalig dem Standardkonto zu. */
    @Query("UPDATE place_entry SET account = :account WHERE account = ''")
    void assignEmptyAccount(String account);

    /** Löscht alle Bewegungen eines Kontos (beim Löschen eines Kontos). */
    @Query("DELETE FROM place_entry WHERE account = :account")
    void deleteByAccount(String account);
}
