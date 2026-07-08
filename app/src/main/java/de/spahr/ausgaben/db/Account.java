package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ein Konto, aus dem bei der Erfassung ausgewählt werden kann. */
@Entity(tableName = "account", indices = {@Index(value = "name", unique = true)})
public class Account {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    /** Währungskennzeichen (z. B. „EUR"/„€"); leer = globale Standardwährung aus den Einstellungen. */
    @NonNull
    @ColumnInfo(name = "currency")
    public String currency = "";

    /**
     * Geschlossenes (inaktives) Konto: nicht mehr auswählbar (Menü/Dropdowns/Bestände/Einzel-Auswertung),
     * zählt nur noch historisch in der Auswertung-Gesamtsicht. Kann wieder geöffnet werden.
     */
    @ColumnInfo(name = "closed")
    public boolean closed;

    /**
     * KMyMoney-Kontotyp (aus dem Import). 0 = unbekannt/manuell → wie Anlage behandelt. Verbindlichkeit sind
     * die Typen 4 (Kreditkarte), 5 (Kredit) und 10 (Verbindlichkeit); alles andere gilt als Anlagekonto.
     */
    @ColumnInfo(name = "acct_type")
    public int kmyType;

    public Account() {
    }

    @Ignore
    public Account(@NonNull String name) {
        this.name = name;
    }

    /** True, wenn dies ein Verbindlichkeitskonto ist (KMyMoney-Typ 4/5/10). */
    public boolean isLiability() {
        return isLiabilityType(kmyType);
    }

    /** True für KMyMoney-Verbindlichkeitstypen: 4 Kreditkarte, 5 Kredit, 10 Verbindlichkeit. */
    public static boolean isLiabilityType(int kmyType) {
        return kmyType == 4 || kmyType == 5 || kmyType == 10;
    }
}
