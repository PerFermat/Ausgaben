package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Zugriff auf die Kategorie-Typen ({@link CategoryType}) aus der KMyMoney-Datei. */
@Dao
public interface CategoryTypeDao {

    /** Setzt/aktualisiert den Typ einer Kategorie (Pfad = Primärschlüssel). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CategoryType type);

    @Query("SELECT * FROM category_type")
    List<CategoryType> getAll();
}
