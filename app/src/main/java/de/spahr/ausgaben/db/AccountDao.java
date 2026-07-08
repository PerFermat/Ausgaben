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

    /** Nur aktive (nicht geschlossene) Konten – für alle Auswahl-/Menü-Ansichten. */
    @Query("SELECT name FROM account WHERE closed = 0 ORDER BY name COLLATE NOCASE ASC")
    List<String> getActiveNames();

    /** Namen der geschlossenen Konten – zum Herausfiltern der automatischen Auflösung. */
    @Query("SELECT name FROM account WHERE closed = 1")
    List<String> getClosedNames();

    /** Aktive Anlagekonten (alles außer Verbindlichkeitstypen 4/5/10), nach Name sortiert. */
    @Query("SELECT name FROM account WHERE closed = 0 AND acct_type NOT IN (4,5,10) "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<String> getAssetNames();

    /** Aktive Verbindlichkeitskonten (KMyMoney-Typ 4/5/10), nach Name sortiert. */
    @Query("SELECT name FROM account WHERE closed = 0 AND acct_type IN (4,5,10) "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<String> getLiabilityNames();

    /** KMyMoney-Kontotyp setzen (beim Import). */
    @Query("UPDATE account SET acct_type = :type WHERE name = :name")
    void setType(String name, int type);

    @Query("SELECT * FROM account")
    List<Account> getAll();

    /** Alle Konten nach Name sortiert (für die Verwaltungsliste mit Status). */
    @Query("SELECT * FROM account ORDER BY name COLLATE NOCASE ASC")
    List<Account> getAllOrdered();

    /** Konto schließen (inaktiv) oder wieder öffnen. */
    @Query("UPDATE account SET closed = :closed WHERE name = :name")
    void setClosed(String name, boolean closed);

    /** Währungskennzeichen eines Kontos setzen. */
    @Query("UPDATE account SET currency = :currency WHERE name = :name")
    void setCurrency(String name, String currency);

    @Query("DELETE FROM account")
    void deleteAll();

    @Query("DELETE FROM account WHERE name = :name")
    void deleteByName(String name);
}
