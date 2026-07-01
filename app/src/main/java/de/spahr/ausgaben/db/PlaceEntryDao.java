package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaceEntryDao {

    @Insert
    long insert(PlaceEntry entry);

    /** Saldo je Ort (Summe der Bewegungen). */
    @Query("SELECT place AS place, SUM(amount_cents) AS balance_cents FROM place_entry GROUP BY place")
    List<PlaceBalance> getBalances();

    /** Aktueller Saldo eines einzelnen Ortes. */
    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM place_entry WHERE place = :place")
    long getBalance(String place);

    /** Summe aller Ort-Bewegungen (für „ohne Ort" = Gesamtsumme − diese Summe). */
    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM place_entry")
    long getTotal();

    /** Bewegungen eines Ortes, chronologisch aufsteigend (für den Verlauf). */
    @Query("SELECT * FROM place_entry WHERE place = :place ORDER BY created_at ASC, id ASC")
    List<PlaceEntry> getByPlace(String place);

    /** Alle Bewegungen, chronologisch aufsteigend (für die Auswertung). */
    @Query("SELECT * FROM place_entry ORDER BY created_at ASC, id ASC")
    List<PlaceEntry> getAll();

    @Query("UPDATE place_entry SET place = :newName WHERE place = :oldName")
    void renamePlace(String oldName, String newName);

    @Query("DELETE FROM place_entry WHERE place = :place")
    void deleteByPlace(String place);
}
