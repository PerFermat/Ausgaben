package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfAbsent(Tag tag);

    @Query("SELECT name FROM tag ORDER BY name COLLATE NOCASE ASC")
    List<String> getAllNames();

    @Query("DELETE FROM tag")
    void deleteAll();
}
