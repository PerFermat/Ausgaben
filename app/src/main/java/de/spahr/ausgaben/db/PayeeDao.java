package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PayeeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfAbsent(Payee payee);

    @Query("SELECT name FROM payee ORDER BY name COLLATE NOCASE ASC")
    List<String> getAllNames();
}
