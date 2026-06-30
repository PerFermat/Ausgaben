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

    @Query("SELECT * FROM booking ORDER BY created_at DESC, id DESC")
    List<Booking> getAllBookings();

    @Query("SELECT * FROM booking WHERE exported = 0 ORDER BY created_at ASC, id ASC")
    List<Booking> getUnexported();

    @Query("UPDATE booking SET exported = 1 WHERE id IN (:ids)")
    void markExported(List<Long> ids);

    @Query("DELETE FROM booking")
    void deleteAll();

    @Query("DELETE FROM booking WHERE account = :account AND exported = 1")
    void deleteExportedByAccount(String account);

    @Query("SELECT DISTINCT category FROM booking WHERE category != '' ORDER BY category COLLATE NOCASE ASC")
    List<String> getDistinctCategories();
}
