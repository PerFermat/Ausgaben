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
     * die Typen 4 (Kreditkarte), 5 (Kredit) und 10 (Verbindlichkeit); Typ 7 ist ein Depot; alles andere gilt
     * als Anlagekonto.
     */
    @ColumnInfo(name = "acct_type")
    public int kmyType;

    /**
     * Sortierplatz innerhalb der eigenen Kontenart. Bei Gleichstand – und damit für alle Konten, solange
     * nichts von Hand sortiert wurde – entscheidet weiterhin der Name.
     */
    @ColumnInfo(name = "sort_pos")
    public int sortPos;

    /** KMyMoney-Kontotyp eines Investment-Kontos; in der App die Trägerzeile eines Depots. */
    public static final int KMY_TYPE_DEPOT = 7;

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

    /**
     * True für die Trägerzeile eines Depots. Sie trägt nur Name, Sortierplatz, Gruppen und den
     * Offen/Geschlossen-Zustand – Wertpapiere, Kurse und der Depotwert liegen weiterhin in den
     * {@code security}-Tabellen, gebucht wird auf ein Depot nicht.
     */
    public boolean isDepot() {
        return kmyType == KMY_TYPE_DEPOT;
    }

    /** True für KMyMoney-Verbindlichkeitstypen: 4 Kreditkarte, 5 Kredit, 10 Verbindlichkeit. */
    public static boolean isLiabilityType(int kmyType) {
        return kmyType == 4 || kmyType == 5 || kmyType == 10;
    }
}
