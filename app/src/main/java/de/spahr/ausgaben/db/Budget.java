package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Jahres-Budget (Soll) für eine Kategorie. {@link #amountCents} ist der Sollwert fürs ganze Jahr
 * (Monatssicht = /12). {@link #source} = {@code "kmy"} (aus KMyMoney importiert, nicht editierbar) oder
 * {@code "internal"} (aus dem Verlauf berechnet bzw. manuell gesetzt).
 */
@Entity(tableName = "budget",
        indices = {@Index(value = {"year", "category", "is_income"}, unique = true)})
public class Budget {

    public static final String SOURCE_KMY = "kmy";
    public static final String SOURCE_INTERNAL = "internal";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "year")
    public int year;

    /** Voller Kategoriepfad („Haupt:Unter" oder „Haupt"). */
    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "is_income")
    public boolean isIncome;

    /** Jahres-Sollbetrag in Cent. */
    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    @NonNull
    @ColumnInfo(name = "source")
    public String source = SOURCE_INTERNAL;

    public Budget() {
    }

    @Ignore
    public Budget(int year, @NonNull String category, boolean isIncome, long amountCents,
                  @NonNull String source) {
        this.year = year;
        this.category = category;
        this.isIncome = isIncome;
        this.amountCents = amountCents;
        this.source = source;
    }
}
