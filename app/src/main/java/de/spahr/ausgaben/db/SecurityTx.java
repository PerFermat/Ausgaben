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

    /**
     * Dieselbe Bewegung wie eine andere — für den Hinweis auf eine doppelt eingelesene Abrechnung.
     *
     * <p>Verglichen werden Depot, Wertpapier, Art, Tag und alle Beträge; nicht verglichen werden
     * {@link #id}, {@link #pending}, Konto und Kategorien: ob eine Buchung schon exportiert wurde oder
     * über welche Kategorie sie läuft, macht sie nicht zu einer anderen Bewegung.</p>
     *
     * <p>Beim Datum zählt der <b>Kalendertag</b>, nicht der Zeitstempel. Eine aus KMyMoney importierte
     * Zeile und eine aus einer Abrechnung gesetzte kommen aus verschiedenen Quellen; auf die Uhrzeit ist
     * dabei kein Verlass, auf den Tag schon.</p>
     */
    public boolean sameMovement(SecurityTx other) {
        return other != null
                && depot.equals(other.depot)
                && securityKmyId.equals(other.securityKmyId)
                && action.equals(other.action)
                && amountCents == other.amountCents
                && netCents == other.netCents
                && feeCents == other.feeCents
                && Math.abs(shares - other.shares) < 1e-6
                && sameDay(date, other.date);
    }

    /** Zwei Zeitstempel am selben Kalendertag (lokale Zeitzone). */
    public static boolean sameDay(long a, long b) {
        java.util.Calendar ca = java.util.Calendar.getInstance();
        java.util.Calendar cb = java.util.Calendar.getInstance();
        ca.setTimeInMillis(a);
        cb.setTimeInMillis(b);
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR)
                && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR);
    }
}
