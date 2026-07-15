package de.spahr.ausgaben.util;

/**
 * Meldet den Fortschritt einer länger laufenden Arbeit (Download, Parsen, Schreiben). Bewusst in einem
 * neutralen Paket, damit {@code net} nicht von {@code export} abhängen muss.
 *
 * <p>Wird aus <b>Hintergrund-Threads</b> gerufen – die Umsetzung muss selbst auf den Main-Thread wechseln.</p>
 */
public interface ProgressListener {

    /**
     * @param done  bereits erledigt (Bytes, Transaktionen, Buchungen …)
     * @param total Gesamtmenge; {@code <= 0} = unbekannt (dann lässt sich kein Prozentwert bilden)
     */
    void onProgress(long done, long total);
}
