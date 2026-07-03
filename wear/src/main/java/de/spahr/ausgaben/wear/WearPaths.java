package de.spahr.ausgaben.wear;

/** Gemeinsame Konstanten der Wear-Anbindung (Data-Layer-Pfade + interne Broadcast-Aktion). */
public final class WearPaths {

    private WearPaths() {
    }

    /** Uhr → Phone: neue gesprochene Ausgabe (JSON {id,text,timestamp}). */
    public static final String PATH_NEW = "/expense/new";
    /** Phone → Uhr: Bestätigung mit der übertragenen ID. */
    public static final String PATH_ACK = "/expense/ack";

    /** Interner Broadcast: offene Anzahl hat sich geändert (Zähler aktualisieren). */
    public static final String ACTION_PENDING_CHANGED = "de.spahr.ausgaben.wear.PENDING_CHANGED";

    /** Buchungstyp (per Knopf gewählt) – Drahtwerte, identisch zu {@code Repository.VOICE_TYPE_*}. */
    public static final String TYPE_INCOME = "income";
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_TRANSFER = "transfer";
}
