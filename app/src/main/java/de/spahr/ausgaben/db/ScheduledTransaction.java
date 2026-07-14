package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Eine geplante Buchung aus KMyMoney ({@code <SCHEDULED_TX>}). Wird bei jedem .kmy-Import komplett neu
 * eingelesen (Tabelle geleert und ersetzt). {@link #kind}: 0 = Auszahlung, 1 = Einzahlung, 2 = Umbuchung.
 * Anzeige nach {@link #nextDueMs} (nächste Fälligkeit) aufsteigend.
 */
@Entity(tableName = "scheduled_transaction")
public class ScheduledTransaction {

    public static final int KIND_EXPENSE = 0;
    public static final int KIND_INCOME = 1;
    public static final int KIND_TRANSFER = 2;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "kmy_id")
    public String kmyId = "";

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "kind")
    public int kind;

    /** Nächste Fälligkeit (ms); Sortierschlüssel. */
    @ColumnInfo(name = "next_due_ms")
    public long nextDueMs;

    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    @NonNull
    @ColumnInfo(name = "payee")
    public String payee = "";

    /** Primäres Konto (Bank/Bargeld) der geplanten Buchung. */
    @NonNull
    @ColumnInfo(name = "account")
    public String account = "";

    /** Kategorie-Pfad bzw. – bei Umbuchung – das Zielkonto. */
    @NonNull
    @ColumnInfo(name = "counterparty")
    public String counterparty = "";

    /** KMyMoney-Wiederholung ({@code occurence}) + Multiplikator – zum Auffalten in die einzelnen Termine. */
    @ColumnInfo(name = "occurrence")
    public int occurrence;

    @ColumnInfo(name = "occurrence_multiplier")
    public int occurrenceMultiplier = 1;

    /** Enddatum der Planung (ms), 0 = unbegrenzt. Begrenzt die Projektion in die Zukunft. */
    @ColumnInfo(name = "end_ms")
    public long endMs;

    /** 1 = Splitbuchung (mehrere Kategorien); die Teile stehen in {@code scheduled_split}. */
    @ColumnInfo(name = "split")
    public int split;

    /**
     * Nur bei Umbuchung relevant: 1 = Geld fließt <b>in</b> {@link #account} (dieses Konto ist das Ziel/„Nach",
     * {@link #counterparty} die Quelle/„Von"); 0 = Geld fließt <b>aus</b> {@link #account} (Quelle/„Von").
     */
    @ColumnInfo(name = "incoming")
    public int incoming;

    /** Nur beim Import gefüllt (nicht persistiert) – wird in {@code scheduled_split} geschrieben. */
    @Ignore
    public java.util.List<ScheduledSplit> splitParts;

    public ScheduledTransaction() {
    }

    @Ignore
    public ScheduledTransaction(@NonNull String kmyId, @NonNull String name, int kind, long nextDueMs,
                                long amountCents, @NonNull String payee, @NonNull String account,
                                @NonNull String counterparty, int occurrence, int occurrenceMultiplier,
                                long endMs) {
        this.kmyId = kmyId;
        this.name = name;
        this.kind = kind;
        this.nextDueMs = nextDueMs;
        this.amountCents = amountCents;
        this.payee = payee;
        this.account = account;
        this.counterparty = counterparty;
        this.occurrence = occurrence;
        this.occurrenceMultiplier = occurrenceMultiplier;
        this.endMs = endMs;
    }
}
