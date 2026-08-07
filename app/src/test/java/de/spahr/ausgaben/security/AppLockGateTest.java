package de.spahr.ausgaben.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests für die Ausnahme der App-Sperre bei selbst gestarteten fremden Apps: Erkennung des Absprungs und
 * Ablauf der Kulanzfrist. Das Zusammenspiel mit dem Activity-Lebenszyklus wird auf dem Gerät geprüft.
 */
public class AppLockGateTest {

    private static final String OWN = "de.spahr.ausgaben";

    @Test
    public void leavesApp_fremdesPaketIstEinAbsprung() {
        assertTrue(AppLockGate.leavesApp(OWN, "com.google.android.GoogleCamera"));
    }

    @Test
    public void leavesApp_eigenesPaketIstKeinAbsprung() {
        assertFalse(AppLockGate.leavesApp(OWN, OWN));
    }

    @Test
    public void leavesApp_impliziterIntentZaehltAlsAbsprung() {
        // Ohne Komponente entscheidet das System – das landet praktisch immer bei einer fremden App.
        assertTrue(AppLockGate.leavesApp(OWN, null));
    }

    @Test
    public void graceExpired_kurzVorDerFristTraegtDieUebergabeNoch() {
        assertFalse(AppLockGate.graceExpired(1_000L, 1_000L + AppLockGate.GRACE_MS - 1));
    }

    @Test
    public void graceExpired_aufDerFristUndDarueberWirdGesperrt() {
        assertTrue(AppLockGate.graceExpired(1_000L, 1_000L + AppLockGate.GRACE_MS));
        assertTrue(AppLockGate.graceExpired(1_000L, 1_000L + 10 * AppLockGate.GRACE_MS));
    }

    @Test
    public void graceExpired_sofortigeRueckkehrSperrtNicht() {
        assertFalse(AppLockGate.graceExpired(5_000L, 5_000L));
    }
}
