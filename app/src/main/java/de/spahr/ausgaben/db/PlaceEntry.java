package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Eine datierte Bewegung eines Bargeld-Ortes (Geldbeutel, Ablageort, …).
 * Getrennt von {@link Booking}: der Ort ist keine Buchungseigenschaft. Der aktuelle Saldo eines
 * Ortes ist die Summe seiner Bewegungen; der Verlauf ergibt sich aus deren Reihenfolge.
 * Wird bei „Datenbank zurücksetzen" und beim Import NICHT gelöscht.
 */
@Entity(tableName = "place_entry")
public class PlaceEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Konto, zu dem der Ort gehört (Orte sind kontobezogen). */
    @NonNull
    @ColumnInfo(name = "account")
    public String account = "";

    @NonNull
    @ColumnInfo(name = "place")
    public String place = "";

    /** Vorzeichenbehaftet: +Zufluss / −Abfluss. */
    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    /** booking | transfer | reconcile */
    @NonNull
    @ColumnInfo(name = "type")
    public String type = "booking";

    public PlaceEntry() {
    }

    public PlaceEntry(@NonNull String account, @NonNull String place, long amountCents,
                      long createdAt, @NonNull String type) {
        this.account = account;
        this.place = place;
        this.amountCents = amountCents;
        this.createdAt = createdAt;
        this.type = type;
    }
}
