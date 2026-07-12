package de.spahr.ausgaben.db;

import androidx.room.ColumnInfo;

/** Saldo je Konto ({@link BookingDao#getAllAccountBalances}) – für die Mehrfach-Konto-Verwaltung. */
public class AccountBalance {

    @ColumnInfo(name = "name")
    public String name;

    /** Saldo in Cent (Einnahmen − Ausgaben). */
    @ColumnInfo(name = "balance")
    public long balance;
}
