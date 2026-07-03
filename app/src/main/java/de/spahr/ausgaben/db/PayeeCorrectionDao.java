package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PayeeCorrectionDao {

    /** Neue Korrektur ablegen bzw. bestehende (gleicher {@code spoken}) ersetzen. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PayeeCorrection correction);

    /** Zuletzt gelernter richtiger Empfänger, dessen {@code spoken} den Begriff als Teilstring enthält. */
    @Query("SELECT corrected FROM payee_correction WHERE spoken LIKE '%' || :term || '%' "
            + "ORDER BY created_at DESC LIMIT 1")
    String findCorrectedBySpokenLike(String term);

    /** Richtiger Empfänger zu einem exakt bekannten {@code spoken}-Begriff. */
    @Query("SELECT corrected FROM payee_correction WHERE spoken = :spoken "
            + "ORDER BY created_at DESC LIMIT 1")
    String findCorrectedBySpokenExact(String spoken);

    /** Alle bekannten (falsch) erkannten Begriffe – als Kandidaten für die unscharfe Suche. */
    @Query("SELECT spoken FROM payee_correction")
    List<String> getAllSpoken();

    @Query("DELETE FROM payee_correction")
    void deleteAll();
}
