package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** Eine wählbare Sprache: {@link #code} (z. B. „de") und Anzeigename {@link #name} (z. B. „Deutsch"). */
@Entity(tableName = "language")
public class Language {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "code")
    public String code = "";

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    public Language() {
    }

    @Ignore
    public Language(@NonNull String code, @NonNull String name) {
        this.code = code;
        this.name = name;
    }
}
