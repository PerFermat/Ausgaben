package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Sortierplatz einer ganzen Kontenart. Damit lassen sich die Blöcke „Anlagekonten",
 * „Verbindlichkeitskonten" und „Depots" als Ganzes verschieben, ohne die Konten darin anzufassen.
 */
@Entity(tableName = "account_kind_order")
public class AccountKindOrder {

    /** Eine der Konstanten aus {@link AccountKind}. */
    @PrimaryKey
    @ColumnInfo(name = "kind")
    public int kind;

    @ColumnInfo(name = "sort_pos")
    public int sortPos;

    public AccountKindOrder() {
    }

    @Ignore
    public AccountKindOrder(int kind, int sortPos) {
        this.kind = kind;
        this.sortPos = sortPos;
    }
}
