package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AccountGroupDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(AccountGroup group);

    /** Favoriten stehen vorn – sie sind der häufigste Griff; dahinter eigene Gruppen, dann die Banken. */
    String ORDER = " ORDER BY CASE source_key WHEN '" + AccountGroup.SOURCE_FAVORITES + "' THEN 0"
            + " ELSE 1 END, auto ASC, sort_pos ASC, name COLLATE NOCASE ASC";

    /**
     * Hat die Gruppe mindestens ein offenes Konto? Nur solche Gruppen erscheinen in der Auswahl – eine
     * Gruppe aus lauter geschlossenen Konten führte dort ins Leere.
     */
    String HAS_OPEN_ACCOUNT = " EXISTS (SELECT 1 FROM account_group_member m"
            + " JOIN account a ON a.id = m.account_id"
            + " WHERE m.group_id = account_group.id AND a.closed = 0)";

    /** Alle Gruppen – für das Zuordnungs-Menü am Konto, das auch stillgelegte Gruppen erreichbar hält. */
    @Query("SELECT * FROM account_group" + ORDER)
    List<AccountGroup> getAll();

    /** Gruppen für die Auswahl: nur die mit mindestens einem offenen Konto. */
    @Query("SELECT * FROM account_group WHERE" + HAS_OPEN_ACCOUNT + ORDER)
    List<AccountGroup> getSelectable();

    @Query("SELECT * FROM account_group WHERE id = :id")
    AccountGroup getById(long id);

    /** Die Gruppe, sofern sie noch wählbar ist; sonst {@code null} (Aufrufer fällt auf „alle" zurück). */
    @Query("SELECT * FROM account_group WHERE id = :id AND" + HAS_OPEN_ACCOUNT)
    AccountGroup getSelectableById(long id);

    @Query("SELECT id FROM account_group WHERE name = :name COLLATE NOCASE")
    Long getIdByName(String name);

    /** Die aus der Datei abgeleitete Gruppe zu ihrem Herkunftskennzeichen, oder {@code null}. */
    @Query("SELECT * FROM account_group WHERE source_key = :sourceKey LIMIT 1")
    AccountGroup getBySourceKey(String sourceKey);

    /** Benennt eine Gruppe um – gebraucht, damit die Favoriten der Sprache der Oberfläche folgen. */
    @Query("UPDATE account_group SET name = :name WHERE id = :id")
    void setName(long id, String name);

    /** Löscht eine Gruppe samt Zuordnungen – Bankgruppen sind davon ausgenommen (siehe Repository). */
    @Query("DELETE FROM account_group WHERE id = :id AND auto = 0")
    void deleteCustom(long id);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addMember(AccountGroupMember member);

    @Query("DELETE FROM account_group_member WHERE group_id = :groupId AND account_id = :accountId")
    void removeMember(long groupId, long accountId);

    /** Alle Gruppen-IDs eines Kontos – für die Häkchen im Drei-Punkte-Menü. */
    @Query("SELECT group_id FROM account_group_member WHERE account_id = :accountId")
    List<Long> getGroupIdsOfAccount(long accountId);

    /** Alle Zuordnungen der Datei-Gruppen entfernen; der Import setzt sie anschließend neu aus der Datei. */
    @Query("DELETE FROM account_group_member WHERE group_id IN "
            + "(SELECT id FROM account_group WHERE auto = 1)")
    void clearAutoMembers();

    /**
     * Entfernt Gruppen ohne jedes Mitglied. Eigene Gruppen verschwinden so, sobald ihr letztes Konto
     * herausgenommen oder gelöscht wurde; Bankgruppen, sobald ihr Institut nicht mehr in der .kmy steht.
     */
    @Query("DELETE FROM account_group WHERE auto = :auto AND NOT EXISTS "
            + "(SELECT 1 FROM account_group_member WHERE group_id = account_group.id)")
    void deleteEmpty(boolean auto);

    // ---- Reihenfolge der Kontenarten ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void setKindOrder(AccountKindOrder order);

    @Query("SELECT * FROM account_kind_order")
    List<AccountKindOrder> getKindOrder();
}
