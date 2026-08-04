package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regeln für die Kassensturz-Vorgabe (Empfänger/Kategorie). */
public class ReconcileTargetTest {

    @Test
    public void ohneBuchungImmerUebernehmbar() {
        assertTrue(ReconcileTarget.canApply(false, "", ""));
        assertTrue(ReconcileTarget.canApply(false, null, null));
    }

    @Test
    public void mitBuchungNurMitBeidenAngaben() {
        assertTrue(ReconcileTarget.canApply(true, "Unbekannt", "Sonstiges"));
        assertFalse(ReconcileTarget.canApply(true, "Unbekannt", ""));
        assertFalse(ReconcileTarget.canApply(true, "  ", "Sonstiges"));
        assertFalse(ReconcileTarget.canApply(true, null, "Sonstiges"));
    }

    @Test
    public void beschriftungNurWennBeidesSteht() {
        assertEquals("Unbekannt / Sonstiges", ReconcileTarget.label(" Unbekannt ", "Sonstiges "));
        assertNull(ReconcileTarget.label("Unbekannt", ""));
        assertNull(ReconcileTarget.label(null, "Sonstiges"));
    }
}
