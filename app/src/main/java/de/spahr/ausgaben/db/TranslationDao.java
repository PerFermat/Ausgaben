package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TranslationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Translation> translations);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertLanguage(Language language);

    @Query("SELECT count(*) FROM translation")
    int count();

    @Query("SELECT key, value FROM translation WHERE lang = :lang")
    List<KeyValue> getPairs(String lang);

    @Query("SELECT * FROM language ORDER BY code = 'de' DESC, code = 'en' DESC, name COLLATE NOCASE ASC")
    List<Language> getLanguages();

    /** Alle Schlüssel/Werte einer Sprache – für die Export-Vorlage. */
    @Query("SELECT key, value FROM translation WHERE lang = :lang ORDER BY key")
    List<KeyValue> getPairsOrdered(String lang);

    @Query("DELETE FROM translation WHERE lang = :lang")
    void deleteTranslations(String lang);

    @Query("DELETE FROM language WHERE code = :lang")
    void deleteLanguage(String lang);

    /** Schmales Schlüssel/Wert-Paar für die In-Memory-Map. */
    class KeyValue {
        @androidx.room.ColumnInfo(name = "key")
        public String key;
        @androidx.room.ColumnInfo(name = "value")
        public String value;
    }
}
