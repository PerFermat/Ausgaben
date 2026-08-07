package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Eine Kontengruppe zur persönlichen Ordnung („Favoriten", „Gemeinschaftskonten", „Volksbank").
 *
 * <p>Anders als die Kontenart ist die Gruppe frei wählbar und nicht ausschließlich: ein Konto kann in
 * beliebig vielen Gruppen stehen. Gruppen mit {@link #auto} = true stammen aus dem Institutsblock der
 * KMyMoney-Datei; sie spiegeln nur die Datei und sind deshalb weder von Hand änderbar noch löschbar.</p>
 */
@Entity(tableName = "account_group", indices = {@Index(value = "name", unique = true)})
public class AccountGroup {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    /** true = beim Import aus dem Bankinstitut erzeugt (unveränderlich), false = selbst angelegt. */
    @ColumnInfo(name = "auto")
    public boolean auto;

    /** Sortierplatz in der Gruppenauswahl; bei Gleichstand entscheidet der Name. */
    @ColumnInfo(name = "sort_pos")
    public int sortPos;

    public AccountGroup() {
    }

    @Ignore
    public AccountGroup(@NonNull String name, boolean auto) {
        this.name = name;
        this.auto = auto;
    }
}
