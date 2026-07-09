package de.spahr.ausgaben.ui;

/**
 * Gemeinsame Farbcodes für die Kontokategorien (Anlage/Verbindlichkeit/Depot),
 * genutzt in der Kontenschublade und in den Beständen. Hell = hellere Farbe
 * (schwarze Schrift), Dunkel = dunklere Farbe (weiße Schrift).
 */
final class CategoryColors {
    private CategoryColors() {}

    static final int LIGHT_ASSET = 0xFFA5D6A7;     // Grün 200
    static final int DARK_ASSET = 0xFF2E7D32;      // Grün 800
    static final int LIGHT_LIABILITY = 0xFFEF9A9A; // Rot 200
    static final int DARK_LIABILITY = 0xFFB71C1C;  // Rot 900
    static final int LIGHT_DEPOT = 0xFF90CAF9;     // Blau 200
    static final int DARK_DEPOT = 0xFF1565C0;      // Blau 800

    // Alte Invers-Darstellung (für die Gesamt-Zeile).
    static final int LIGHT_TOTAL = 0xFF303030;
    static final int DARK_TOTAL = 0xFF808080;
}
