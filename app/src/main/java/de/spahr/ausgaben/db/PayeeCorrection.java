package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Eine gelernte Namenskorrektur für die Spracherkennung: ein (falsch) erkannter Begriff {@link #spoken}
 * (klein geschrieben abgelegt) und der vom Nutzer bestätigte richtige Empfänger {@link #corrected}.
 * Wird konsultiert, wenn ein gesprochener Name in den Buchungen nicht gefunden wird.
 */
@Entity(tableName = "payee_correction", indices = {@Index(value = "spoken", unique = true)})
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

    public PayeeCorrection() {
    }

    @Ignore
    public PayeeCorrection(@NonNull String spoken, @NonNull String corrected, long createdAt) {
        this.spoken = spoken;
        this.corrected = corrected;
        this.createdAt = createdAt;
    }
}
