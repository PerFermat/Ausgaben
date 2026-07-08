package de.spahr.ausgaben.wear;

/**
 * GMS-freie Konstanten der Wear-Anbindung, die in beiden Flavors (full/foss) verfügbar sein müssen. Der
 * eigentliche Data-Layer-Listener liegt nur im „full"-Flavor; im „foss"-Flavor (F-Droid) wird der zugehörige
 * Broadcast schlicht nie gesendet – die Empfänger-Registrierung bleibt dann wirkungslos.
 */
public final class WearBridge {

    private WearBridge() {
    }

    /** Broadcast, den der Wear-Listener nach dem Anlegen einer per Uhr gesprochenen Buchung sendet. */
    public static final String ACTION_BOOKINGS_CHANGED = "de.spahr.ausgaben.BOOKINGS_CHANGED";
}
