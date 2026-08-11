package de.spahr.ausgaben.ui;

/**
 * Glättet die Fortschrittsanzeige des Import-Banners: die angezeigte Zahl läuft dem gemeldeten Wert
 * weich nach und kriecht weiter, solange keine neue Meldung kommt. So steht die Anzeige auch in
 * Abschnitten nicht still, die von sich aus nichts melden (Entpacken, geplante Buchungen).
 *
 * <p>Reine Rechnung ohne Android – die Zeit wird hineingereicht, damit sie prüfbar bleibt. Die Zahl
 * steigt <b>monoton</b>: eine Meldung unter dem bereits Angezeigten wird verworfen, bis der echte
 * Fortschritt sie eingeholt hat.</p>
 */
public final class ProgressSmoother {

    /** Taktweite der Anzeige. */
    public static final long TICK_MS = 40;

    /** Ohne neue Meldung rückt die Anzeige um 1 % je Zeitscheibe vor. */
    static final long CREEP_MS = 1500;

    /** Weiter als so viel darf das Kriechen dem letzten gemeldeten Wert nicht vorauseilen. */
    static final int CREEP_MAX = 5;

    /** Ohne {@link #finish()} bleibt die Anzeige darunter – die 100 gehören dem Ende. */
    static final int CEILING = 99;

    private int shown;
    private int target;
    private boolean finished;

    /** Zeitpunkt, seit dem die Anzeige auf dem gemeldeten Wert steht ({@code -1} = sie läuft noch nach). */
    private long creepSince = -1;

    /** Meldung aus dem Import (nur nach oben wirksam). Kommt aus einem Hintergrund-Thread. */
    public synchronized void report(int percent, long nowMs) {
        int p = Math.max(0, Math.min(CEILING, percent));
        if (p > target) {
            target = p;
            creepSince = -1;
        }
    }

    /**
     * Rückt einen Takt vor und liefert den nun anzuzeigenden Wert. Gehört in den Anzeige-Takt, nicht
     * in eine Abfrage nebenbei – jeder Aufruf bewegt die Zahl.
     */
    public synchronized int tick(long nowMs) {
        if (finished) {
            shown = 100;
            return shown;
        }
        if (shown < target) {
            // Ein Viertel des Abstands je Takt, mindestens 1: aus 30 % Rückstand werden ~0,4 Sekunden.
            shown += Math.max(1, (target - shown) / 4);
            if (shown >= target) {
                shown = target;
                creepSince = nowMs;
            } else {
                creepSince = -1;
            }
            return shown;
        }
        if (creepSince < 0) {
            creepSince = nowMs;
            return shown;
        }
        int limit = Math.min(target + CREEP_MAX, CEILING);
        int crept = (int) ((nowMs - creepSince) / CREEP_MS);
        int want = Math.min(limit, target + crept);
        if (want > shown) {
            shown = want;
        }
        return shown;
    }

    /** Zuletzt angezeigter Wert, ohne die Zahl zu bewegen. */
    public synchronized int shown() {
        return shown;
    }

    /** Der Import ist durch: 100 %. */
    public synchronized void finish() {
        finished = true;
        shown = 100;
    }

    /** Setzt alles auf Anfang – für den nächsten Import in derselben Ansicht. */
    public synchronized void reset() {
        shown = 0;
        target = 0;
        finished = false;
        creepSince = -1;
    }
}
