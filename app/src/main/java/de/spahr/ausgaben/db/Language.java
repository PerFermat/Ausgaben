package de.spahr.ausgaben.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Eine wählbare Sprache: {@link #code} (z. B. „de") und Anzeigename {@link #name} (z. B. „Deutsch"), dazu
 * die Vorbelegungen {@link #defaultCurrency} und {@link #numberFormat}, die das (schlanke) Onboarding
 * beim Anlegen eines neuen Profils automatisch aus der gewählten Sprache übernimmt – ohne eigene Felder
 * für Währung/Zahlenformat im Assistenten.
 */
@Entity(tableName = "language")
public class Language {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "code")
    public String code = "";

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @NonNull
    @ColumnInfo(name = "defaultCurrency")
    public String defaultCurrency = "€";

    /** Einer der {@code SettingsStore.NUMBER_FORMAT_*}-Werte. */
    @NonNull
    @ColumnInfo(name = "numberFormat")
    public String numberFormat = "plain_comma";

    public Language() {
    }

    @Ignore
    public Language(@NonNull String code, @NonNull String name) {
        this.code = code;
        this.name = name;
    }

    @Ignore
    public Language(@NonNull String code, @NonNull String name, @NonNull String defaultCurrency,
                     @NonNull String numberFormat) {
        this.code = code;
        this.name = name;
        this.defaultCurrency = defaultCurrency;
        this.numberFormat = numberFormat;
    }
}
