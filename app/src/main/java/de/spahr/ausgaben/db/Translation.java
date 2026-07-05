package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

/** Ein übersetzter Text: {@link #value} für den {@link #key} in der Sprache {@link #lang}. */
@Entity(tableName = "translation", primaryKeys = {"lang", "key"})
public class Translation {

    @NonNull
    @ColumnInfo(name = "lang")
    public String lang = "";

    @NonNull
    @ColumnInfo(name = "key")
    public String key = "";

    @NonNull
    @ColumnInfo(name = "value")
    public String value = "";

    public Translation() {
    }

    @Ignore
    public Translation(@NonNull String lang, @NonNull String key, @NonNull String value) {
        this.lang = lang;
        this.key = key;
        this.value = value;
    }
}
