package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfAbsent(Account account);

    @Query("SELECT name FROM account ORDER BY name COLLATE NOCASE ASC")
    List<String> getAllNames();

    @Query("DELETE FROM account")
    void deleteAll();

    @Query("DELETE FROM account WHERE name = :name")
    void deleteByName(String name);
}
