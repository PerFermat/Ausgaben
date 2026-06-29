package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ein Geldempfänger, aus dem bei der Erfassung ausgewählt werden kann. */
@Entity(tableName = "payee", indices = {@Index(value = "name", unique = true)})
public class Payee {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    public Payee() {
    }

    @Ignore
    public Payee(@NonNull String name) {
        this.name = name;
    }
}
