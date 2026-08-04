package de.spahr.ausgaben.net.smb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Anmelde- und Portwahl-Regeln. Beides ist bewusst als reine Entscheidung ohne Netz und ohne smbj
 * herausgezogen, damit genau die Fälle prüfbar sind, die in der Praxis schiefgingen.
 */
public class SmbSessionsTest {

    // ---- Wer ist überhaupt ein Benutzer? ----

    @Test
    public void domainPrefixIsNotAUserName() {
        assertEquals("hts", SmbSessions.userNameOf("hts"));
        assertEquals("hts", SmbSessions.userNameOf("GRUPPE\\hts"));
        assertEquals("hts", SmbSessions.userNameOf("GRUPPE/hts"));
        // Der Assistent belegt das Feld mit „GRUPPE\" vor – das ist kein eingegebener Benutzername.
        assertEquals("", SmbSessions.userNameOf("GRUPPE\\"));
        assertEquals("", SmbSessions.userNameOf("  "));
        assertEquals("", SmbSessions.userNameOf(null));
    }

    // ---- Wann ist eine Gast-Sitzung in Ordnung? ----

    @Test
    public void guestIsFineWithoutAPassword() {
        assertTrue(SmbSessions.guestAllowed("", ""));
        assertTrue(SmbSessions.guestAllowed("GRUPPE\\", ""));
        // Passwortlose Freigabe: Benutzername eingetragen, aber kein Passwort – Gast ist gewollt.
        assertTrue(SmbSessions.guestAllowed("hts", ""));
        assertTrue(SmbSessions.guestAllowed("hts", null));
    }

    @Test
    public void guestStaysSuspiciousWhenAPasswordWasTyped() {
        // Der Schutz aus 1.6: Samba nimmt mit „map to guest = bad password" auch ein falsches
        // Passwort an und stuft still auf Gast herunter – das darf nicht als Erfolg durchgehen.
        assertFalse(SmbSessions.guestAllowed("hts", "falsch"));
        assertFalse(SmbSessions.guestAllowed("GRUPPE\\hts", "falsch"));
    }

    // ---- Welcher Port wird angesprochen? ----

    /** Merkt sich die geprüften Ports und meldet nur die angegebenen als erreichbar. */
    private static final class Probe implements SmbSessions.PortProbe {
        final List<Integer> asked = new ArrayList<>();
        private final List<Integer> open;

        Probe(Integer... open) {
            this.open = java.util.Arrays.asList(open);
        }

        @Override
        public boolean reachable(String host, int port) {
            asked.add(port);
            return open.contains(port);
        }
    }

    @Test
    public void defaultPortIsUsedWithoutAnyProbing() {
        Probe probe = new Probe();
        assertEquals(0, SmbSessions.choosePort("server", 0, probe));
        assertEquals(445, SmbSessions.choosePort("server", 445, probe));
        assertTrue("kein Vorab-Test nötig", probe.asked.isEmpty());
    }

    @Test
    public void configuredPortWinsWhenItAnswers() {
        Probe probe = new Probe(7777, 445);
        assertEquals(7777, SmbSessions.choosePort("server", 7777, probe));
        assertEquals(java.util.Collections.singletonList(7777), probe.asked);
    }

    @Test
    public void fallsBackToTheStandardPort() {
        // Der Fall aus dem Fehlerbericht: mDNS meldete 7777, gehorcht wird aber auf 445.
        Probe probe = new Probe(445);
        assertEquals(445, SmbSessions.choosePort("server", 7777, probe));
        assertEquals(java.util.Arrays.asList(7777, 445), probe.asked);
    }

    @Test
    public void keepsTheConfiguredPortWhenNothingAnswers() {
        // Server aus – dann soll die Meldung vom eingetragenen Port handeln, nicht von 445.
        Probe probe = new Probe();
        assertEquals(7777, SmbSessions.choosePort("server", 7777, probe));
    }
}
