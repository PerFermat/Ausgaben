package de.spahr.ausgaben.export;

/**
 * Die Prozentbereiche der Import-Phasen – an <b>einer</b> Stelle, weil Konto-, Depot- und Geplant-Import je
 * eine eigene Kopie der Banner-Logik haben und sonst auseinanderlaufen.
 *
 * <pre>
 *   0–30  Datei herunterladen (gelesene Bytes)
 *  30–45  entpacken und Datei lesen (Teilschritte von {@link KmyDocument})
 *  45–70  Buchungen lesen (Nenner: TRANSACTIONS-count)
 *  70–99  speichern (je Buchung)
 *    100  fertig
 * </pre>
 *
 * Gemeldet wird stets <b>nachlaufend</b> (nach getaner Arbeit) – die alte Anzeige meldete den Wert vor der
 * Arbeit und stand deshalb genau währenddessen still.
 */
public final class ImportPhase {

    public static final int DOWNLOAD_FROM = 0;
    public static final int DOWNLOAD_TO = 30;
    public static final int READ_FILE_FROM = 30;
    public static final int READ_FILE_TO = 45;
    public static final int BOOKINGS_FROM = 45;
    public static final int BOOKINGS_TO = 70;
    public static final int SAVE_FROM = 70;
    public static final int SAVE_TO = 99;
    public static final int DONE = 100;

    private ImportPhase() {
    }

    /**
     * Bildet {@code done/total} auf den Bereich {@code from..to} ab. Unbekannte Gesamtmenge
     * ({@code total <= 0}) → {@code from} (die Phase beginnt, wir wissen nur nicht, wie weit sie ist).
     */
    public static int map(long done, long total, int from, int to) {
        if (total <= 0) {
            return from;
        }
        long clamped = Math.max(0, Math.min(done, total));
        return from + (int) Math.round((to - from) * (double) clamped / total);
    }
}
