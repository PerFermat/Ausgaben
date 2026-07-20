package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Ein Kategorie-Teil einer Splitbuchung. Die Summe der Teilbeträge einer Buchung ergibt deren
 * {@link Booking#amountCents}. Teilbeträge sind vorzeichenbehaftet (auch negativ erlaubt) und in der
 * gleichen Größenordnung wie der (positive) Gesamtbetrag der Buchung.
 * Wird nur bei Buchungen mit mindestens zwei Kategorien angelegt; Einzel-Kategorien bleiben in
 * {@link Booking#category}.
 */
@Entity(tableName = "booking_split")
public class BookingSplit {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "booking_id")
    public long bookingId;

    @NonNull
    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "amount_cents")
    public long amountCents;

    /** Typ von {@link #category} je Zeile; siehe {@link Booking#categoryIsIncome}. */
    @ColumnInfo(name = "category_is_income")
    public Boolean categoryIsIncome;

    public BookingSplit() {
    }

    @Ignore
    public BookingSplit(long bookingId, String category, long amountCents) {
        this.bookingId = bookingId;
        this.category = category == null ? "" : category;
        this.amountCents = amountCents;
    }

    @Ignore
    public BookingSplit(long bookingId, String category, long amountCents, Boolean categoryIsIncome) {
        this(bookingId, category, amountCents);
        this.categoryIsIncome = categoryIsIncome;
    }
}
