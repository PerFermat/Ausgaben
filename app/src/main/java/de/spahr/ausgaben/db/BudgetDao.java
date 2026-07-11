package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Budget budget);

    @Query("SELECT * FROM budget WHERE year = :year")
    List<Budget> getForYear(int year);

    @Query("DELETE FROM budget WHERE year = :year")
    void deleteYear(int year);

    /** Herkunft des Budgets eines Jahres ({@code "kmy"}/{@code "internal"}); {@code null} wenn keins vorhanden. */
    @Query("SELECT source FROM budget WHERE year = :year LIMIT 1")
    String sourceForYear(int year);
}
