package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AccountDao {

    /**
     * Reihenfolge aller Kontenlisten: erst der selbst vergebene Sortierplatz, bei Gleichstand der Name.
     * Solange nichts sortiert wurde, steht überall 0 – dann bleibt es bei der bisherigen alphabetischen
     * Reihenfolge.
     */
    String ORDER = " ORDER BY sort_pos ASC, name COLLATE NOCASE ASC";

    /**
     * Einschränkung auf eine Kontengruppe. {@code groupId <= 0} bedeutet „alle Konten" – so bedient
     * dieselbe Abfrage den gefilterten und den ungefilterten Fall.
     */
    String IN_GROUP = " AND (:groupId <= 0 OR id IN "
            + "(SELECT account_id FROM account_group_member WHERE group_id = :groupId))";

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertRaw(Account account);

    /** Legt ein Konto nur an, wenn es einen nicht-leeren Namen hat (kein namenloses Konto). */
    default void insertIfAbsent(Account account) {
        if (account != null && account.name != null && !account.name.trim().isEmpty()) {
            insertRaw(account);
        }
    }

    @Query("SELECT name FROM account" + ORDER)
    List<String> getAllNames();

    /** Nur aktive (nicht geschlossene) Konten – für alle Auswahl-/Menü-Ansichten. */
    @Query("SELECT name FROM account WHERE closed = 0" + ORDER)
    List<String> getActiveNames();

    /**
     * Aktive Konten, auf die gebucht werden kann – ohne die Trägerzeilen der Depots, deren Wert sich aus
     * Stückzahlen und Kursen ergibt und die eine Buchung gar nicht aufnehmen können.
     */
    @Query("SELECT name FROM account WHERE closed = 0 AND acct_type <> 7" + ORDER)
    List<String> getBookableNames();

    /** Namen der geschlossenen Konten – zum Herausfiltern der automatischen Auflösung. */
    @Query("SELECT name FROM account WHERE closed = 1")
    List<String> getClosedNames();

    /** Aktive Anlagekonten (weder Verbindlichkeit 4/5/10 noch Depot 7). */
    @Query("SELECT name FROM account WHERE closed = 0 AND acct_type NOT IN (4,5,7,10)"
            + IN_GROUP + ORDER)
    List<String> getAssetNames(long groupId);

    /** Aktive Verbindlichkeitskonten (KMyMoney-Typ 4/5/10). */
    @Query("SELECT name FROM account WHERE closed = 0 AND acct_type IN (4,5,10)"
            + IN_GROUP + ORDER)
    List<String> getLiabilityNames(long groupId);

    /** Aktive Depots (Trägerzeilen mit KMyMoney-Typ 7). */
    @Query("SELECT name FROM account WHERE closed = 0 AND acct_type = 7"
            + IN_GROUP + ORDER)
    List<String> getDepotNames(long groupId);

    default List<String> getAssetNames() {
        return getAssetNames(0L);
    }

    default List<String> getLiabilityNames() {
        return getLiabilityNames(0L);
    }

    default List<String> getDepotNames() {
        return getDepotNames(0L);
    }

    /** Alle aktiven Konten einer Gruppe – Grundlage des Mehrkonten-Filters in der Buchungsliste. */
    @Query("SELECT name FROM account WHERE closed = 0"
            + " AND id IN (SELECT account_id FROM account_group_member WHERE group_id = :groupId)"
            + ORDER)
    List<String> getNamesInGroup(long groupId);

    /** KMyMoney-Kontotyp setzen (beim Import). */
    @Query("UPDATE account SET acct_type = :type WHERE name = :name")
    void setType(String name, int type);

    @Query("SELECT * FROM account")
    List<Account> getAll();

    /** Alle Konten – auch geschlossene – für die Verwaltungsliste. */
    @Query("SELECT * FROM account" + ORDER)
    List<Account> getAllOrdered();

    @Query("SELECT id FROM account WHERE name = :name")
    Long getIdByName(String name);

    /** Sortierplatz innerhalb der Kontenart setzen. */
    @Query("UPDATE account SET sort_pos = :sortPos WHERE id = :id")
    void setSortPos(long id, int sortPos);

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
