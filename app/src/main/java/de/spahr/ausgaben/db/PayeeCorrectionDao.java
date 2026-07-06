package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PayeeCorrectionDao {

    /** Neuen Alias ablegen bzw. bestehenden (gleicher {@code spoken}) ersetzen. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PayeeCorrection correction);

    /** Alle Aliase (für die Verwaltungsseite). */
    @Query("SELECT * FROM payee_correction ORDER BY spoken COLLATE NOCASE ASC")
    List<PayeeCorrection> getAll();

    @Query("SELECT * FROM payee_correction WHERE id = :id LIMIT 1")
    PayeeCorrection getById(long id);

    @Query("DELETE FROM payee_correction WHERE id = :id")
    void deleteById(long id);

    /** Alias (bevorzugt/übrig), dessen {@code spoken} den Begriff als Teilstring enthält. */
    @Query("SELECT * FROM payee_correction WHERE preferred = :pref "
            + "AND spoken LIKE '%' || :term || '%' ORDER BY created_at DESC LIMIT 1")
    PayeeCorrection findBySpokenLike(String term, int pref);

    /** Alle Aliase (bevorzugt/übrig), deren {@code spoken} den Begriff enthält – für die GPS-Auswahl. */
    @Query("SELECT * FROM payee_correction WHERE preferred = :pref "
            + "AND spoken LIKE '%' || :term || '%' ORDER BY created_at DESC")
    List<PayeeCorrection> findAllBySpokenLike(String term, int pref);

    /** Alias (bevorzugt/übrig) zu einem exakt bekannten {@code spoken}-Begriff. */
    @Query("SELECT * FROM payee_correction WHERE preferred = :pref AND spoken = :spoken "
            + "ORDER BY created_at DESC LIMIT 1")
    PayeeCorrection findBySpokenExact(String spoken, int pref);

    /** Alle Aliase (bevorzugt/übrig) zu einem exakt bekannten {@code spoken}-Begriff – für die GPS-Auswahl. */
    @Query("SELECT * FROM payee_correction WHERE preferred = :pref AND spoken = :spoken "
            + "ORDER BY created_at DESC")
    List<PayeeCorrection> findAllBySpokenExact(String spoken, int pref);

    /** Erkannte Begriffe der bevorzugten bzw. übrigen Aliase – Kandidaten für die unscharfe Suche. */
    @Query("SELECT spoken FROM payee_correction WHERE preferred = :pref")
    List<String> getSpokenByPreferred(int pref);

    /** Aliase (bevorzugt/übrig) mit hinterlegtem Standort – für die Betrag-only-Erfassung per GPS. */
    @Query("SELECT * FROM payee_correction WHERE preferred = :pref AND (lat != 0 OR lon != 0)")
    List<PayeeCorrection> getWithGps(int pref);

    @Query("DELETE FROM payee_correction")
    void deleteAll();
}
