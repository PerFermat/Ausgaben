package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/** Zugriff auf die Kategorie-Teile geplanter Splitbuchungen ({@link ScheduledSplit}). */
@Dao
public interface ScheduledSplitDao {

    @Insert
    void insert(ScheduledSplit part);

    @Query("DELETE FROM scheduled_split")
    void deleteAll();

    @Query("SELECT * FROM scheduled_split WHERE scheduled_id = :scheduledId")
    List<ScheduledSplit> getForScheduled(long scheduledId);

    /** Alle Split-Teile auf einmal (für die Kategorien-Auswertung; in einer Abfrage statt je Planung). */
    @Query("SELECT * FROM scheduled_split")
    List<ScheduledSplit> getAll();
}
