package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Eine in einer <b>Umbuchung</b> versteckte Ausgabe/Einnahme – typischerweise die Gebühr (oder Steuer)
 * eines Wertpapierkaufs/-verkaufs. Solche Beträge erscheinen bewusst <b>nicht</b> in der Buchungsliste und
 * ändern <b>keine</b> Salden (die Umbuchung bewegt das Geld bereits). Sie werden nur von den
 * Einnahmen/Ausgaben-Auswertungen je Kategorie ausgewertet (Kategorien-Seite, Budget), die Umbuchungen
 * sonst ignorieren. Wird – wie die importierten Buchungen – je Konto beim Import komplett ersetzt.
 */
@Entity(tableName = "analysis_extra")
public class AnalysisExtra {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Konto der zugehörigen Umbuchung (zum Ersetzen je Konto beim Import). */
    @NonNull
    @ColumnInfo(name = "account")
    public String account = "";

    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    /** {@code false} = Ausgabe (z. B. Gebühr), {@code true} = Einnahme. */
    @ColumnInfo(name = "is_income")
    public boolean isIncome;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public AnalysisExtra() {
    }

    @Ignore
    public AnalysisExtra(@NonNull String account, @NonNull String category, long amountCents,
                         boolean isIncome, long createdAt) {
        this.account = account;
        this.category = category;
        this.amountCents = amountCents;
        this.isIncome = isIncome;
        this.createdAt = createdAt;
    }
}
