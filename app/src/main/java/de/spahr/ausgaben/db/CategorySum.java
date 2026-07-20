package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;

/** Ergebniszeile je Kategorie ({@link BookingDao#getCategoryActuals}). */
public class CategorySum {

    @ColumnInfo(name = "category")
    public String category;

    /**
     * Netto-Geldzufluss in Cent, <b>vorzeichenbehaftet</b> ({@code +} = Zufluss, {@code −} = Abfluss).
     * Die Einordnung als Einnahme/Ausgabe erfolgt über den Kategorietyp, nicht über dieses Vorzeichen.
     */
    @ColumnInfo(name = "total")
    public long total;

    /**
     * Kategorietyp dieser Zeile (true = Einnahme, false = Ausgabe), {@code null} = unbekannt (Zeile vor
     * der Kategorietyp-je-Zeile-Migration). Getrennt je Typ gruppiert, damit gleichnamige Einnahme-/
     * Ausgabekategorien (z. B. „Versicherung:Krankenzusatz") nicht vermischt werden.
     */
    @ColumnInfo(name = "cat_type")
    public Boolean catIsIncome;
}
