package de.spahr.ausgaben.export;

/**
 * Der feste Kopf des Fortschrittsbalkens – bis die Datei gelesen ist, ist jeder Lauf gleich:
 *
 * <pre>
 *   0–30  Datei herunterladen (gelesene Bytes)
 *  30–45  entpacken und Datei lesen (Teilschritte von {@link KmyDocument})
 *    100  fertig
 * </pre>
 *
 * Was danach kommt, hängt vom Lauf ab (Konten? Depots? Planungen?) und wird nach der gemessenen
 * Arbeitsmenge aufgeteilt – siehe {@link ImportBudget}.
 *
 * <p>Gemeldet wird stets <b>nachlaufend</b> (nach getaner Arbeit) – die alte Anzeige meldete den Wert
 * vor der Arbeit und stand deshalb genau währenddessen still.</p>
 */
public final class ImportPhase {

    public static final int DOWNLOAD_FROM = 0;
    public static final int DOWNLOAD_TO = 30;
    public static final int READ_FILE_FROM = 30;
    public static final int READ_FILE_TO = 45;
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
