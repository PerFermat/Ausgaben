package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Stichwort aus KMyMoney. Die Liste wird bei jedem Zugriff auf die {@code .kmy}-Datei neu
 * übernommen; die App legt selbst nie eines an, denn eingebbar ist nur, was es dort schon gibt.
 */
@Entity(tableName = "tag", indices = {@Index(value = "name", unique = true)})
public class Tag {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    public Tag() {
    }

    @Ignore
    public Tag(@NonNull String name) {
        this.name = name;
    }
}
