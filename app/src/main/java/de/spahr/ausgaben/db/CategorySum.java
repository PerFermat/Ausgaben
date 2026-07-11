package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;

/** Ergebniszeile der Ist-Summen je Kategorie ({@link BookingDao#getCategoryActuals}). */
public class CategorySum {

    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "is_income")
    public boolean isIncome;

    /** Summe in Cent (Betrag, positiv). */
    @ColumnInfo(name = "total")
    public long total;
}
