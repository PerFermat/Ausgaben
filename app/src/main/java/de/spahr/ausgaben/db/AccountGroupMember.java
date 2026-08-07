package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

/**
 * Zuordnung eines Kontos zu einer Kontengruppe. Verweist auf die Konto-ID, damit ein Umbenennen des
 * Kontos die Zuordnung nicht verliert; verschwindet mit dem Konto bzw. der Gruppe.
 */
@Entity(tableName = "account_group_member",
        primaryKeys = {"group_id", "account_id"},
        indices = {@Index("account_id")},
        foreignKeys = {
                @ForeignKey(entity = AccountGroup.class, parentColumns = "id",
                        childColumns = "group_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class, parentColumns = "id",
                        childColumns = "account_id", onDelete = ForeignKey.CASCADE)})
public class AccountGroupMember {

    @ColumnInfo(name = "group_id")
    public long groupId;

    @ColumnInfo(name = "account_id")
    public long accountId;

    public AccountGroupMember() {
    }

    @Ignore
    public AccountGroupMember(long groupId, long accountId) {
        this.groupId = groupId;
        this.accountId = accountId;
    }
}
