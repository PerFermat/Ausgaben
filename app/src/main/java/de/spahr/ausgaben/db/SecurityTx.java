package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Eine Depot-Bewegung eines Wertpapiers (Kauf/Verkauf/Dividende/Einbuchung), importiert aus KMyMoney.
 * Reine Anzeige-/Auswertungsdaten; wirkt nicht auf die Konto-Salden (der Geldfluss läuft über das jeweilige
 * Geldkonto und wird dort separat importiert).
 */
@Entity(tableName = "security_tx", indices = {@Index(value = "depot")})
public class SecurityTx {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "depot")
    public String depot = "";

    /** KMyMoney-Wertpapier-ID (verknüpft mit {@link Security#kmyId}). */
    @NonNull
    @ColumnInfo(name = "security_kmy_id")
    public String securityKmyId = "";

    @NonNull
    @ColumnInfo(name = "security_name")
    public String securityName = "";

    @ColumnInfo(name = "date")
    public long date;

    /** buy | sell | dividend | add | remove | reinvest */
    @NonNull
    @ColumnInfo(name = "action")
    public String action = "";

    /** Vorzeichenbehaftete Stückzahl (Kauf/Add +, Verkauf/Remove −, Dividende 0). */
    @ColumnInfo(name = "shares")
    public double shares;

    /** Geldbetrag in Cent (Kauf = Kosten, Verkauf = Erlös, Dividende = <b>Brutto</b>, Add/Remove = 0). */
    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    /**
     * Netto-Betrag in Cent: bei Dividenden das tatsächlich gutgeschriebene Geld (Brutto − Steuer); bei allen
     * anderen Aktionen gleich {@link #amountCents}. Steuert die Brutto/Netto-Anzeige der Dividenden.
     */
    @ColumnInfo(name = "net_cents")
    public long netCents;

    /**
     * Gebühren in Cent (immer ≥ 0): bei Kauf/Verkauf/Wiederanlage die Kosten, die in KMyMoney als eigener
     * Ausgabe-Kategorie-Split neben dem Wertpapier-Split hängen. {@link #amountCents} enthält sie nicht.
     * Bei Dividenden 0 – dort stecken die Abzüge in der Differenz {@link #amountCents} − {@link #netCents}.
     */
    @ColumnInfo(name = "fee_cents")
    public long feeCents;

    /**
     * In der App erfasst und noch nicht in die KMyMoney-Datei geschrieben. Solche Bewegungen überleben
     * einen Depot-Reimport (siehe {@code SecurityDao.deleteTx}); nach dem Export fällt das Kennzeichen
     * weg und die Bewegung wird beim nächsten Import wie jede andere aus der Datei ersetzt.
     */
    @ColumnInfo(name = "pending")
    public boolean pending;

    /**
     * Geldkonto der Gegenbuchung (Belastung beim Kauf, Gutschrift bei Verkauf/Dividende). Wird beim
     * Import aus dem Geld-Split mitgelesen und dient in der Erfassungsmaske als Vorbelegung.
     */
    @NonNull
    @ColumnInfo(name = "money_account")
    public String moneyAccount = "";

    /** Kategorie der Gebühren (Kauf/Verkauf) bzw. der Steuer (Dividende). */
    @NonNull
    @ColumnInfo(name = "fee_category")
    public String feeCategory = "";

    /** Ertragskategorie einer Dividende; der Bruttobetrag hängt in KMyMoney daran. */
    @NonNull
    @ColumnInfo(name = "income_category")
    public String incomeCategory = "";

    /** Zugehörige Geldbuchung ({@code Booking.id}); 0 = keine (alle importierten Bewegungen). */
    @ColumnInfo(name = "booking_id")
    public long bookingId;

    public SecurityTx() {
    }

    @Ignore
    public SecurityTx(@NonNull String depot, @NonNull String securityKmyId, @NonNull String securityName,
                      long date, @NonNull String action, double shares, long amountCents) {
        this(depot, securityKmyId, securityName, date, action, shares, amountCents, amountCents);
    }

    @Ignore
    public SecurityTx(@NonNull String depot, @NonNull String securityKmyId, @NonNull String securityName,
                      long date, @NonNull String action, double shares, long amountCents, long netCents) {
        this(depot, securityKmyId, securityName, date, action, shares, amountCents, netCents, 0L);
    }

    @Ignore
    public SecurityTx(@NonNull String depot, @NonNull String securityKmyId, @NonNull String securityName,
                      long date, @NonNull String action, double shares, long amountCents, long netCents,
                      long feeCents) {
        this.depot = depot;
        this.securityKmyId = securityKmyId;
        this.securityName = securityName;
        this.date = date;
        this.action = action;
        this.shares = shares;
        this.amountCents = amountCents;
        this.netCents = netCents;
        this.feeCents = feeCents;
    }
}
