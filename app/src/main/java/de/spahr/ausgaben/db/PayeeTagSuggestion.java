package de.spahr.ausgaben.db;

import java.util.List;

/**
 * Was die App über die Stichwörter eines Empfängers weiß: die Liste für den Vorspann und den Wert,
 * mit dem eine neue Buchung vorbelegt wird. Beides kommt aus derselben Abfrage – siehe
 * {@link PayeeTags}.
 */
public final class PayeeTagSuggestion {

    /** Die Stichwörter des Empfängers in Rangfolge, höchstens {@link PayeeTags#LIMIT}. */
    public final List<String> ranked;

    /** Speicherwert für {@code booking.tags}; leer, wenn nichts vorzubelegen ist. */
    public final String preset;

    public PayeeTagSuggestion(List<String> ranked, String preset) {
        this.ranked = ranked;
        this.preset = preset == null ? "" : preset;
    }
}
