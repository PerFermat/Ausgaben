package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein gelernter Alias für die Spracherkennung: ein (falsch) erkannter Begriff {@link #spoken} (klein
 * geschrieben abgelegt) und der richtige Empfänger {@link #corrected}. Zusätzlich können – damit ein Alias
 * jede Buchungsart abdeckt – ein {@link #account}, je bis zu zwei Kategorien für Einnahmen/Ausgaben sowie
 * Von-/Bis-Konto für Umbuchungen hinterlegt werden. Wird bei der Sprach-Erfassung konsultiert.
 */
// Unique über (spoken, corrected): derselbe gesprochene Begriff darf mehrfach vorkommen, solange er auf
// verschiedene Empfänger zeigt (z. B. „rewe" → „Rewe Ort1" und „rewe" → „Rewe Ort2", per GPS unterschieden).
@Entity(tableName = "payee_correction",
        indices = {@Index(value = {"spoken", "corrected"}, unique = true)})
public class PayeeCorrection {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "spoken")
    public String spoken = "";

    @NonNull
    @ColumnInfo(name = "corrected")
    public String corrected = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;

    /** Konto für Einnahme/Ausgabe (leer = Standardkonto). */
    @NonNull
    @ColumnInfo(name = "account")
    public String account = "";

    /** Bis zu zwei Kategorien für Einnahmen. */
    @NonNull
    @ColumnInfo(name = "cat_income_1")
    public String catIncome1 = "";

    @NonNull
    @ColumnInfo(name = "cat_income_2")
    public String catIncome2 = "";

    /** Bis zu zwei Kategorien für Ausgaben. */
    @NonNull
    @ColumnInfo(name = "cat_expense_1")
    public String catExpense1 = "";

    @NonNull
    @ColumnInfo(name = "cat_expense_2")
    public String catExpense2 = "";

    /** Quell-/Zielkonto für Umbuchungen. */
    @NonNull
    @ColumnInfo(name = "from_account")
    public String fromAccount = "";

    @NonNull
    @ColumnInfo(name = "to_account")
    public String toAccount = "";

    /** Ort für Einnahme/Ausgabe (leer = keiner/Standardort). */
    @NonNull
    @ColumnInfo(name = "place")
    public String place = "";

    /** Von-/Nach-Ort für Umbuchungen (leer = keiner). */
    @NonNull
    @ColumnInfo(name = "from_place")
    public String fromPlace = "";

    @NonNull
    @ColumnInfo(name = "to_place")
    public String toPlace = "";

    /** true = dieser Alias wird vor der Suche in bestehenden Buchungen berücksichtigt. */
    @ColumnInfo(name = "preferred")
    public boolean preferred;

    /** Bevorzugte Buchungsart (income/expense/transfer); leer = Ausgabe. Am Phone maßgeblich; Wear nimmt
     * die per Knopf mitgegebene Art. */
    @NonNull
    @ColumnInfo(name = "type")
    public String type = "";

    /** Standort des Alias (beim Lernen aus der Buchung übernommen); {@code 0/0} = keiner. Für die
     * Betrag-only-Erfassung: passt der aktuelle Standort (≤100 m), liefert der Alias die Buchungsdaten. */
    @ColumnInfo(name = "lat")
    public double lat;

    @ColumnInfo(name = "lon")
    public double lon;

    public PayeeCorrection() {
    }

    @Ignore
    public PayeeCorrection(@NonNull String spoken, @NonNull String corrected, long createdAt) {
        this.spoken = spoken;
        this.corrected = corrected;
        this.createdAt = createdAt;
    }
}
