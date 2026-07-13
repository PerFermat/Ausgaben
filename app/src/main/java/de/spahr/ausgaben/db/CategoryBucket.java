package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;

/**
 * Historische Zahlungs-Magnitude einer Kategorie in einem Zeit-Bucket (Tag im Monat 1–31 bzw. Monat
 * im Jahr 1–12) – Grundlage der verlaufsbasierten Balkenfarbe im Budget.
 */
public class CategoryBucket {

    @ColumnInfo(name = "category")
    public String category;

    /** Tag im Monat (1–31) oder Monat im Jahr (1–12), je nach Abfrage. */
    @ColumnInfo(name = "bucket")
    public int bucket;

    /** Summe der Beträge (Magnitude, Cent) in diesem Bucket. */
    @ColumnInfo(name = "total")
    public long total;
}
