package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Budget (Soll) für eine Kategorie. {@link #month} = 0 bedeutet Jahres-Soll (Monatssicht = /12),
 * 1–12 ein konkreter Monat (dann liegen bis zu 12 Zeilen je Kategorie/Jahr vor, aus KMyMoney
 * {@code monthbymonth}). {@link #amountCents} ist der Sollbetrag der jeweiligen Ebene.
 * {@link #source} = {@code "kmy"} (aus KMyMoney importiert, nicht editierbar) oder {@code "internal"}
 * (aus dem Verlauf berechnet bzw. manuell gesetzt).
 */
@Entity(tableName = "budget",
        indices = {@Index(value = {"year", "month", "category", "is_income"}, unique = true)})
public class Budget {

    public static final String SOURCE_KMY = "kmy";
    public static final String SOURCE_INTERNAL = "internal";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "year")
    public int year;

    /** 0 = Jahres-Soll (Monatssicht = /12); 1–12 = Sollwert für genau diesen Monat. */
    @ColumnInfo(name = "month")
    public int month;

    /** Voller Kategoriepfad („Haupt:Unter" oder „Haupt"). */
    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "is_income")
    public boolean isIncome;

    /** Sollbetrag in Cent (Jahr bei {@code month=0}, sonst des Monats). */
    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    @NonNull
    @ColumnInfo(name = "source")
    public String source = SOURCE_INTERNAL;

    public Budget() {
    }

    /** Jahres-Soll ({@code month=0}) – für internes/berechnetes/manuelles Budget. */
    @Ignore
    public Budget(int year, @NonNull String category, boolean isIncome, long amountCents,
                  @NonNull String source) {
        this(year, 0, category, isIncome, amountCents, source);
    }

    @Ignore
    public Budget(int year, int month, @NonNull String category, boolean isIncome, long amountCents,
                  @NonNull String source) {
        this.year = year;
        this.month = month;
        this.category = category;
        this.isIncome = isIncome;
        this.amountCents = amountCents;
        this.source = source;
    }
}
