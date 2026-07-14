package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Ein Kategorie-Teil einer geplanten Splitbuchung ({@link ScheduledTransaction} mit {@code split == 1}).
 * Analog zu {@link BookingSplit}, aber an eine geplante Buchung ({@code scheduled_id}) gebunden. Wird bei
 * jedem .kmy-Import zusammen mit den geplanten Buchungen komplett neu geschrieben.
 */
@Entity(tableName = "scheduled_split")
public class ScheduledSplit {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "scheduled_id")
    public long scheduledId;

    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    public ScheduledSplit() {
    }

    @Ignore
    public ScheduledSplit(long scheduledId, @NonNull String category, long amountCents) {
        this.scheduledId = scheduledId;
        this.category = category;
        this.amountCents = amountCents;
    }
}
