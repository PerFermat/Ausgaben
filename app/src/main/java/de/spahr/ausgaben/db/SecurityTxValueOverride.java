package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

/**
 * Manuell gesetzter Geldwert für eine Ein-/Ausbuchung (Aktion "add"/"remove"), die KMyMoney selbst nie
 * mit einem Wert versieht (nur Stückzahl). Verknüpft über den einzigen über einen Depot-Reimport hinweg
 * stabilen Schlüssel – nicht über {@link SecurityTx#id}, das bei jedem Reimport neu vergeben wird.
 */
@Entity(tableName = "security_tx_value_override",
        primaryKeys = {"depot", "security_kmy_id", "date", "action", "shares"})
public class SecurityTxValueOverride {

    @NonNull
    @ColumnInfo(name = "depot")
    public String depot = "";

    @NonNull
    @ColumnInfo(name = "security_kmy_id")
    public String securityKmyId = "";

    @ColumnInfo(name = "date")
    public long date;

    /** add | remove */
    @NonNull
    @ColumnInfo(name = "action")
    public String action = "";

    @ColumnInfo(name = "shares")
    public double shares;

    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    public SecurityTxValueOverride() {
    }

    @Ignore
    public SecurityTxValueOverride(@NonNull String depot, @NonNull String securityKmyId, long date,
                                   @NonNull String action, double shares, long amountCents) {
        this.depot = depot;
        this.securityKmyId = securityKmyId;
        this.date = date;
        this.action = action;
        this.shares = shares;
        this.amountCents = amountCents;
    }
}
