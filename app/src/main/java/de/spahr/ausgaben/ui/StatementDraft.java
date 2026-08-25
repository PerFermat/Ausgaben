package de.spahr.ausgaben.ui;

import android.os.Parcel;
import android.os.Parcelable;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.SecurityTx;
import de.spahr.ausgaben.util.SecurityAmounts;

/**
 * Eine eingelesene Abrechnung auf dem Weg zur Buchung — noch nichts Gespeichertes, sondern der Stand
 * dessen, was aus der Datei herausgelesen und vom Nutzer berichtigt wurde.
 *
 * <p>Beim Einlesen mehrerer Abrechnungen auf einmal reicht ein einzelner Vorgang nicht mehr: die
 * Erkennungsliste ({@link StatementBatchActivity}) hält für jede Datei einen solchen Entwurf, gibt ihn
 * zum Berichtigen an die Erfassungsmaske und bekommt ihn von dort zurück. Erst „Alle speichern" macht
 * daraus Bewegungen und Geldbuchungen.</p>
 *
 * <p>Der Entwurf urteilt nicht selbst über Zahlen — {@link SecurityAmounts} ergänzt sie, {@link #problem()}
 * sagt nur, was zum Buchen noch fehlt. Damit gilt in der Liste dieselbe Rechnung und dieselbe Prüfung wie
 * in der Maske.</p>
 */
public class StatementDraft implements Parcelable {

    static final String BUY = "buy";
    static final String SELL = "sell";
    static final String DIVIDEND = "dividend";

    /** Anzeigename der Datei — das Einzige, was von einer unlesbaren Abrechnung bleibt. */
    public String fileName = "";
    /** Die schon in die Belegablage kopierte Abrechnung; beim Speichern wird sie zum Beleg. */
    public String stagedPath;
    /** Zwischengespeicherter Text der Abrechnung; die Maske zeigt daraus die Datumsauswahl. */
    public String textPath;

    public String isin;
    public String depot = "";
    public String kmyId = "";
    public String securityName = "";

    /** Eine der Aktionen {@code buy}, {@code sell}, {@code dividend}; {@code null} = nicht erkannt. */
    public String action;
    public long dateMillis = -1;
    public Double shares;
    public Double price;
    public Long grossCents;
    public Long feeCents;
    public Long netCents;

    public String moneyAccount = "";
    public String feeCategory = "";
    public String incomeCategory = "";

    /**
     * Warum die Datei gar nicht erst ausgewertet werden konnte (Textbaustein), sonst 0. Ein solcher
     * Eintrag bleibt in der Liste stehen, statt still zu verschwinden — sonst suchte der Nutzer eine
     * Buchung, die es nie gab.
     */
    public int failure;
    /** Brutto, Steuer und Netto gehen nicht auf; dann wird nicht gerechnet, sondern berichtigt. */
    public boolean conflict;

    public StatementDraft() {
    }

    public boolean isDividend() {
        return DIVIDEND.equals(action);
    }

    /**
     * Ergänzt die fehlenden Zahlen — genau wie die Maske, nur ohne Anzeige. Alle abgelesenen Werte
     * stehen dabei fest ({@code keepGiven}): der Stückpreis der Bank ist genauer als einer, den man aus
     * Summe geteilt durch Stückzahl zurückrechnet.
     *
     * <p>Ohne Steuersatz: ein Entwurf stammt immer aus einer eingelesenen Abrechnung, und dort hat die
     * Regel gesucht. Was sie nicht fand, wurde nicht abgezogen — eine gerechnete Steuer wäre erfunden.</p>
     */
    public void resolve() {
        SecurityAmounts.Input in = new SecurityAmounts.Input();
        in.action = action == null ? BUY : action;
        in.keepGiven = true;
        in.shares = isDividend() ? null : shares;
        in.price = isDividend() ? null : price;
        in.grossCents = grossCents;
        in.feeCents = feeCents;
        in.netCents = netCents;

        SecurityAmounts.Result r = SecurityAmounts.solve(in);
        conflict = r.conflict;
        if (conflict) {
            return;
        }
        shares = isDividend() ? null : r.shares;
        price = isDividend() ? null : r.price;
        grossCents = r.grossCents;
        feeCents = r.feeCents;
        netCents = r.netCents;
    }

    /**
     * Was dem Eintrag zum Buchen fehlt, als Textbaustein; 0, wenn nichts fehlt. Die Reihenfolge geht vom
     * Grundsätzlichen zum Beiläufigen — genannt wird immer nur der erste Grund, denn wer die Datei nicht
     * lesen kann, dem nützt der Hinweis auf das fehlende Datum nichts.
     */
    public int problem() {
        if (failure != 0) {
            return failure;
        }
        if (kmyId.isEmpty()) {
            // Ohne ISIN kann die App gar nicht suchen; mit einer unbekannten immerhin fragen. Seit die
            // Zuordnung auch über Kennnummer und Kürzel geht, ist eine fehlende ISIN kein Ausschluss
            // mehr — sie wird erst zum Mangel, wenn auch sonst nichts passt.
            return isin == null || isin.isEmpty()
                    ? R.string.statement_problem_isin : R.string.statement_problem_security;
        }
        if (action == null) {
            return R.string.statement_problem_action;
        }
        if (conflict) {
            return R.string.security_tx_conflict;
        }
        if (dateMillis <= 0) {
            return R.string.statement_problem_date;
        }
        if (grossCents == null || grossCents <= 0 || netCents == null) {
            return R.string.statement_problem_amounts;
        }
        if (!isDividend() && (shares == null || shares <= 0)) {
            return R.string.statement_problem_shares;
        }
        if (moneyAccount.trim().isEmpty()) {
            return R.string.statement_problem_account;
        }
        return 0;
    }

    public boolean isBookable() {
        return problem() == 0;
    }

    /** Die Bewegung, wie sie im Depot steht — dieselben Regeln wie in {@code SecurityTxEditActivity}. */
    public SecurityTx toTx() {
        SecurityTx tx = new SecurityTx();
        tx.depot = depot;
        tx.securityKmyId = kmyId;
        tx.securityName = securityName;
        tx.date = dateMillis;
        tx.action = action;
        double count = shares == null ? 0 : Math.abs(shares);
        // Eine Dividende bewegt keine Stücke; der Bestand am Ex-Tag steht nicht in der Abrechnung.
        tx.shares = isDividend() ? 0 : (SELL.equals(action) ? -count : count);
        long gross = grossCents == null ? 0 : grossCents;
        tx.amountCents = gross;
        tx.netCents = isDividend() ? (netCents == null ? 0 : netCents) : gross;
        tx.feeCents = isDividend() ? 0 : Math.abs(feeCents == null ? 0 : feeCents);
        tx.moneyAccount = moneyAccount.trim();
        tx.feeCategory = feeCategory.trim();
        tx.incomeCategory = isDividend() ? incomeCategory.trim() : "";
        return tx;
    }

    /**
     * Die Geldbuchung zur Bewegung: eine Umbuchung zwischen Geldkonto und Wertpapier. Beim Kauf verlässt
     * das Geld das Konto, bei Verkauf und Dividende kommt es an — bei der Dividende der Nettobetrag,
     * denn nur der wird gutgeschrieben.
     */
    public Booking toBooking() {
        Booking b = new Booking();
        b.account = moneyAccount.trim();
        b.isTransfer = true;
        b.transferAccount = securityName;
        b.isIncome = !BUY.equals(action);
        b.amountCents = Math.abs(netCents == null ? 0 : netCents);
        b.payee = securityName;
        b.createdAt = dateMillis;
        b.category = "";
        return b;
    }

    // ---- Parcelable ----

    protected StatementDraft(Parcel in) {
        fileName = orEmpty(in.readString());
        stagedPath = in.readString();
        textPath = in.readString();
        isin = in.readString();
        depot = orEmpty(in.readString());
        kmyId = orEmpty(in.readString());
        securityName = orEmpty(in.readString());
        action = in.readString();
        dateMillis = in.readLong();
        shares = (Double) in.readValue(Double.class.getClassLoader());
        price = (Double) in.readValue(Double.class.getClassLoader());
        grossCents = (Long) in.readValue(Long.class.getClassLoader());
        feeCents = (Long) in.readValue(Long.class.getClassLoader());
        netCents = (Long) in.readValue(Long.class.getClassLoader());
        moneyAccount = orEmpty(in.readString());
        feeCategory = orEmpty(in.readString());
        incomeCategory = orEmpty(in.readString());
        failure = in.readInt();
        conflict = in.readInt() != 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(fileName);
        out.writeString(stagedPath);
        out.writeString(textPath);
        out.writeString(isin);
        out.writeString(depot);
        out.writeString(kmyId);
        out.writeString(securityName);
        out.writeString(action);
        out.writeLong(dateMillis);
        out.writeValue(shares);
        out.writeValue(price);
        out.writeValue(grossCents);
        out.writeValue(feeCents);
        out.writeValue(netCents);
        out.writeString(moneyAccount);
        out.writeString(feeCategory);
        out.writeString(incomeCategory);
        out.writeInt(failure);
        out.writeInt(conflict ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<StatementDraft> CREATOR = new Creator<StatementDraft>() {
        @Override
        public StatementDraft createFromParcel(Parcel in) {
            return new StatementDraft(in);
        }

        @Override
        public StatementDraft[] newArray(int size) {
            return new StatementDraft[size];
        }
    };

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
