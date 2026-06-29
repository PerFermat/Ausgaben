package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ein Konto, aus dem bei der Erfassung ausgewählt werden kann. */
@Entity(tableName = "account", indices = {@Index(value = "name", unique = true)})
public class Account {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    public Account() {
    }

    @Ignore
    public Account(@NonNull String name) {
        this.name = name;
    }
}
