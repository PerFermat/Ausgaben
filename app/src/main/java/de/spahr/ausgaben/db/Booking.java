package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.List;

/**
 * Eine einzelne Bargeld-Buchung.
 * Der Betrag wird immer positiv in Cent gespeichert; das Vorzeichen ergibt sich
 * aus {@link #isIncome} (Einnahme = positiv, Ausgabe = negativ im Export).
 */
@Entity(tableName = "booking")
public class Booking {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    @ColumnInfo(name = "is_income")
    public boolean isIncome;

    @NonNull
    @ColumnInfo(name = "payee")
    public String payee = "";

    @NonNull
    @ColumnInfo(name = "account")
    public String account = "";

    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    @NonNull
    @ColumnInfo(name = "note")
    public String note = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "exported")
    public boolean exported;

    /** Umbuchung (Kontotransfer): dann ist {@link #transferAccount} das Gegenkonto und es gibt keine Kategorie. */
    @ColumnInfo(name = "is_transfer")
    public boolean isTransfer;

    /** Gegenkonto einer Umbuchung (Anzeigename). */
    @NonNull
    @ColumnInfo(name = "transfer_account")
    public String transferAccount = "";

    /** Verknüpft die beiden Seiten einer in der App erstellten Umbuchung (leer bei importierten Einseitern). */
    @NonNull
    @ColumnInfo(name = "transfer_group")
    public String transferGroup = "";

    /** Transient: Kategorie-Teile einer Splitbuchung (nicht in dieser Tabelle gespeichert, siehe {@link BookingSplit}). */
    @Ignore
    public List<BookingSplit> parts;

    public Booking() {
    }
}
