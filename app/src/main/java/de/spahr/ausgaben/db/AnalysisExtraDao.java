package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

/**
 * Zugriff auf {@link AnalysisExtra} – die in Umbuchungen versteckten Ausgaben/Einnahmen (Gebühren/Steuern),
 * die nur die Kategorien-Auswertungen nutzen. Gelesen wird direkt in den Auswertungs-Queries des
 * {@link BookingDao} (per {@code UNION}); hier nur Schreiben/Löschen.
 */
@Dao
public interface AnalysisExtraDao {

    @Insert
    void insert(AnalysisExtra extra);

    @Query("DELETE FROM analysis_extra WHERE account = :account")
    void deleteByAccount(String account);

    @Query("DELETE FROM analysis_extra")
    void deleteAll();
}
