package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Intrinsischer Typ einer Kategorie aus der KMyMoney-Datei: {@code true} = Einnahme (KMyMoney-Typ 12),
 * {@code false} = Ausgabe (Typ 13). Einzige verlässliche Typ-Quelle für die Budget-Einordnung – der
 * Betrag einer Buchung darf negativ sein, die Kategorie bleibt in ihrem hier gespeicherten Typ.
 * Wird bei jedem .kmy-Import aktualisiert ({@link Repository#applyCategoryTypes}).
 */
@Entity(tableName = "category_type")
public class CategoryType {

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "is_income")
    public boolean isIncome;

    public CategoryType() {
    }

    @Ignore
    public CategoryType(@NonNull String category, boolean isIncome) {
        this.category = category;
        this.isIncome = isIncome;
    }
}
