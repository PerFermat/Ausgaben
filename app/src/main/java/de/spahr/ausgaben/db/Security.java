package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Wertpapier eines importierten Depots (KMyMoney-Investment-Konto). Der aktuelle Kurs ist der letzte
 * aus der KMyMoney-Datei; die gehaltene Stückzahl ergibt sich aus den {@link SecurityTx}-Bewegungen.
 */
@Entity(tableName = "security", indices = {@Index(value = {"depot", "kmy_id"}, unique = true)})
public class Security {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Anzeigename des Depots (KMyMoney-Investment-Konto), zu dem das Wertpapier gehört. */
    @NonNull
    @ColumnInfo(name = "depot")
    public String depot = "";

    /** KMyMoney-Wertpapier-ID (z. B. „E000001"). */
    @NonNull
    @ColumnInfo(name = "kmy_id")
    public String kmyId = "";

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @NonNull
    @ColumnInfo(name = "symbol")
    public String symbol = "";

    /** Handelswährung (z. B. „EUR"). */
    @NonNull
    @ColumnInfo(name = "currency")
    public String currency = "";

    /** Letzter bekannter Kurs (in der Depot-Währung). */
    @ColumnInfo(name = "price")
    public double price;

    /** Datum des letzten Kurses (ms). */
    @ColumnInfo(name = "price_date")
    public long priceDate;

    public Security() {
    }

    @Ignore
    public Security(@NonNull String depot, @NonNull String kmyId, @NonNull String name,
                    @NonNull String symbol, @NonNull String currency, double price, long priceDate) {
        this.depot = depot;
        this.kmyId = kmyId;
        this.name = name;
        this.symbol = symbol;
        this.currency = currency;
        this.price = price;
        this.priceDate = priceDate;
    }
}
