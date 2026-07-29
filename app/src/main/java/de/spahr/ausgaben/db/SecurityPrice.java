package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein historischer Kurs eines Wertpapiers eines Depots (aus dem KMyMoney-PRICES-Block). Anders als der in
 * {@link Security#price} gespeicherte <b>letzte</b> Kurs wird hier die <b>vollständige</b> Kurshistorie
 * abgelegt, damit der Depotwert zu jedem vergangenen Stichtag bewertet werden kann (Vermögensgrafik).
 */
@Entity(tableName = "security_price",
        indices = {@Index(value = {"depot", "security_kmy_id", "date"})})
public class SecurityPrice {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Anzeigename des Depots, zu dem das Wertpapier gehört. */
    @NonNull
    @ColumnInfo(name = "depot")
    public String depot = "";

    /** KMyMoney-Wertpapier-ID (z. B. „E000001"). */
    @NonNull
    @ColumnInfo(name = "security_kmy_id")
    public String securityKmyId = "";

    /** Kursdatum (ms). */
    @ColumnInfo(name = "date")
    public long date;

    /** Kurs (in der Handelswährung des Wertpapiers). */
    @ColumnInfo(name = "price")
    public double price;

    public SecurityPrice() {
    }

    @Ignore
    public SecurityPrice(@NonNull String depot, @NonNull String securityKmyId, long date, double price) {
        this.depot = depot;
        this.securityKmyId = securityKmyId;
        this.date = date;
        this.price = price;
    }
}
