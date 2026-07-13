package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/** Zugriff auf die geplanten Buchungen ({@link ScheduledTransaction}) aus KMyMoney. */
@Dao
public interface ScheduledTransactionDao {

    @Insert
    void insert(ScheduledTransaction tx);

    @Query("DELETE FROM scheduled_transaction")
    void deleteAll();

    /** Nach nächster Fälligkeit aufsteigend. */
    @Query("SELECT * FROM scheduled_transaction ORDER BY next_due_ms ASC, name COLLATE NOCASE ASC")
    List<ScheduledTransaction> getAllByDue();
}
