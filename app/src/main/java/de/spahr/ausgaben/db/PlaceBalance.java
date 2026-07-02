package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;

/** Ergebnis-POJO: Saldo eines Ortes (Summe seiner Bewegungen). */
public class PlaceBalance {
    @ColumnInfo(name = "account")
    public String account;

    @ColumnInfo(name = "place")
    public String place;

    @ColumnInfo(name = "balance_cents")
    public long balanceCents;
}
