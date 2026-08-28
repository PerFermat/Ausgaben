package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Kategorie-Teil einer Depotbewegung — das Gegenstück zu {@link BookingSplit} für
 * {@link SecurityTx}.
 *
 * <p>In KMyMoney hängen an einer Wertpapierbuchung beliebig viele Kategoriesplits, und zwar in zwei
 * Rollen: der Ertrag einer Dividende und die abgezogenen Steuern bzw. Gebühren. Beide Rollen führen
 * hier dieselbe Tabelle und unterscheiden sich allein in {@link #income}. Die Summe der Teile einer
 * Rolle ergibt den Betrag, der in der Maske darüber steht — beim Ertrag das Brutto, bei Steuer und
 * Gebühr das Gebührenfeld.</p>
 *
 * <p>Vorher trug jede Bewegung genau eine Kategorie je Rolle, und Kapitalertragsteuer,
 * Solidaritätszuschlag und Kirchensteuer mussten zu einer Zahl addiert werden — die dann unter einer
 * dieser drei Kategorien stand und dort falsch war.</p>
 */
@Entity(tableName = "security_tx_split", indices = {@Index("tx_id")})
public class SecurityTxSplit {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "tx_id")
    public long txId;

    /** {@code true} = Ertragsteil einer Dividende, {@code false} = Steuer- bzw. Gebührenteil. */
    @ColumnInfo(name = "income")
    public boolean income;

    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    /** Teilbetrag, immer positiv — das Vorzeichen ergibt sich aus der Rolle. */
    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    /**
     * Die Beschriftung, unter der dieser Betrag in der Abrechnung stand („Kapitalertragsteuer").
     *
     * <p>Sie ist der Schlüssel, über den eine gelernte Vorlage beim nächsten Mal wiederfindet, welche
     * Kategorie zu welchem Betrag gehört — reihenfolgeunabhängig und auch dann, wenn eine Zeile
     * einmal fehlt. Leer bleibt sie bei fest programmierten Banken, die nichts zu lernen haben und
     * ihre Teile nur der Reihe nach liefern, und bei Bestandsdaten aus der Zeit vor den Teilen.</p>
     */
    @NonNull
    @ColumnInfo(name = "label")
    public String label = "";

    /** Reihenfolge in der Maske. */
    @ColumnInfo(name = "sort")
    public int sort;

    public SecurityTxSplit() {
    }

    @Ignore
    public SecurityTxSplit(long txId, boolean income, String category, long amountCents,
                           String label, int sort) {
        this.txId = txId;
        this.income = income;
        this.category = category == null ? "" : category;
        this.amountCents = amountCents;
        this.label = label == null ? "" : label;
        this.sort = sort;
    }
}
