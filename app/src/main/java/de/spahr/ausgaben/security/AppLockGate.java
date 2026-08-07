package de.spahr.ausgaben.security;

/**
 * Regeln für die Ausnahme der App-Sperre bei selbst gestarteten fremden Apps.
 *
 * <p>Die Sperre erkennt den Wechsel in den Hintergrund am Zähler gestarteter Activities. Startet die App
 * jedoch selbst eine fremde App – Kamera, Galerie, Dateiauswahl, Spracherkennung –, sieht das für den
 * Zähler genauso aus wie ein Weggehen des Nutzers, obwohl er die App aus seiner Sicht nie verlassen hat.
 * Für diesen Fall wird die Sperre eine Weile ausgesetzt: lange genug, um ein Foto zu machen, kurz genug,
 * dass ein liegengelassenes Gerät geschützt bleibt.</p>
 *
 * <p>Reine Rechenregeln ohne Android-Bezug, damit sie im JVM-Test festgehalten werden können.</p>
 */
public final class AppLockGate {

    /** Kulanzfrist für selbst gestartete fremde Apps. */
    public static final long GRACE_MS = 5 * 60 * 1000L;

    private AppLockGate() {
    }

    /**
     * Führt ein Absprung aus der eigenen App heraus?
     *
     * @param ownPackage    eigener Paketname
     * @param targetPackage Zielpaket des Intents, {@code null} bei einem impliziten Intent (der landet
     *                      praktisch immer bei einer fremden App und zählt daher als Absprung)
     */
    public static boolean leavesApp(String ownPackage, String targetPackage) {
        return targetPackage == null || !targetPackage.equals(ownPackage);
    }

    /** War die App länger als die Kulanzfrist im Hintergrund? */
    public static boolean graceExpired(long leftAt, long now) {
        return now - leftAt >= GRACE_MS;
    }
}
