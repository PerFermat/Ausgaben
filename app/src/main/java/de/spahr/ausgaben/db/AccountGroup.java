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
 * beliebig vielen Gruppen stehen. Gruppen mit {@link #auto} = true stammen aus der KMyMoney-Datei –
 * aus dem Institutsblock oder aus den bevorzugten Konten; sie spiegeln nur die Datei und sind deshalb
 * weder von Hand änderbar noch löschbar.</p>
 */
@Entity(tableName = "account_group", indices = {@Index(value = "name", unique = true)})
public class AccountGroup {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    /** true = beim Import aus der Datei erzeugt (unveränderlich), false = selbst angelegt. */
    @ColumnInfo(name = "auto")
    public boolean auto;

    /** Kennzeichen aus dem Wortschatz für Kontengruppen aus der Datei. */
    public static final String SOURCE_BANK = "bank";
    public static final String SOURCE_FAVORITES = "favorites";

    /**
     * Herkunft einer abgeleiteten Gruppe: leer bei selbst angelegten, sonst {@link #SOURCE_BANK} oder
     * {@link #SOURCE_FAVORITES}. Nötig, weil der Name der Favoritengruppe übersetzt ist und mit der
     * Sprache wechselt – wiederfinden lässt sie sich nur an diesem Kennzeichen.
     */
    @NonNull
    @ColumnInfo(name = "source_key")
    public String sourceKey = "";

    /** Sortierplatz in der Gruppenauswahl; bei Gleichstand entscheidet der Name. */
    @ColumnInfo(name = "sort_pos")
    public int sortPos;

    public AccountGroup() {
    }

    @Ignore
    public AccountGroup(@NonNull String name, boolean auto) {
        this(name, auto, "");
    }

    @Ignore
    public AccountGroup(@NonNull String name, boolean auto, @NonNull String sourceKey) {
        this.name = name;
        this.auto = auto;
        this.sourceKey = sourceKey;
    }
}
