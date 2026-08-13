package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Die eine Regel, aus der der Status „bearbeitet" entsteht – und die verhindert, daß er von Hand zu
 * setzen ist.
 */
public class EditStatusTest {

    private static Booking exportiert(String konto, long cents, long zeit) {
        Booking b = new Booking();
        b.id = 1;
        b.account = konto;
        b.amountCents = cents;
        b.isIncome = false;
        b.createdAt = zeit;
        b.exported = true;
        return b;
    }

    /** Der geänderte Stand, wie ihn der Editor abliefert: Schalter „exportiert" blieb an. */
    private static Booking geaendert(String konto, long cents, long zeit) {
        Booking b = new Booking();
        b.id = 1;
        b.account = konto;
        b.amountCents = cents;
        b.isIncome = false;
        b.createdAt = zeit;
        b.exported = true;
        return b;
    }

    @Test
    public void schalterBliebAnAlsoBearbeitet() {
        Booking alt = exportiert("Bargeld", 250, 1000L);
        Booking neu = geaendert("Girokonto", 400, 2000L);

        EditStatus.apply(alt, neu, true);

        assertTrue(neu.edited);
        assertFalse("bearbeitet und exportiert schließen sich aus", neu.exported);
        assertEquals("Bargeld", neu.origAccount);
        assertEquals(-250, neu.origSignedCents);
        assertEquals(1000L, neu.origCreatedAt);
    }

    @Test
    public void schalterVonHandAusgelegtBleibtNurNichtExportiert() {
        Booking alt = exportiert("Bargeld", 250, 1000L);
        Booking neu = geaendert("Bargeld", 400, 1000L);
        neu.exported = false;

        EditStatus.apply(alt, neu, true);

        assertFalse(neu.edited);
        assertFalse(neu.exported);
        assertEquals("", neu.origAccount);
    }

    @Test
    public void zweiteAenderungBehaeltDieErsteSignatur() {
        Booking alt = exportiert("Bargeld", 400, 2000L);
        alt.exported = false;
        alt.edited = true;
        alt.origAccount = "Bargeld";
        alt.origSignedCents = -250;
        alt.origCreatedAt = 1000L;
        Booking neu = geaendert("Bargeld", 900, 3000L);

        EditStatus.apply(alt, neu, true);

        assertTrue(neu.edited);
        assertFalse(neu.exported);
        assertEquals(-250, neu.origSignedCents);
        assertEquals(1000L, neu.origCreatedAt);
    }

    @Test
    public void ohneKmyModusBleibtAllesWieBisher() {
        Booking alt = exportiert("Bargeld", 250, 1000L);
        Booking neu = geaendert("Bargeld", 400, 1000L);

        EditStatus.apply(alt, neu, false);

        assertFalse(neu.edited);
        assertTrue(neu.exported);
    }

    @Test
    public void nochNieExportiertBleibtEinfachNeu() {
        Booking alt = exportiert("Bargeld", 250, 1000L);
        alt.exported = false;
        Booking neu = geaendert("Bargeld", 400, 1000L);
        neu.exported = false;

        EditStatus.apply(alt, neu, true);

        assertFalse(neu.edited);
        assertFalse(neu.exported);
    }

    @Test
    public void ohneAltenStandWirdNichtsGesetzt() {
        Booking neu = geaendert("Bargeld", 400, 1000L);

        EditStatus.apply(null, neu, true);

        assertFalse(neu.edited);
        assertTrue(neu.exported);
    }

    /** Umbuchung: beide neu angelegten Zeilen erben Status und Signatur. */
    @Test
    public void beideUmbuchungsseitenErbenDenStatus() {
        Booking vorlage = new Booking();
        vorlage.edited = true;
        vorlage.origAccount = "Bargeld";
        vorlage.origSignedCents = -1000;
        vorlage.origCreatedAt = 1000L;
        Booking seite = new Booking();

        EditStatus.inherit(vorlage, seite);

        assertTrue(seite.edited);
        assertFalse(seite.exported);
        assertEquals("Bargeld", seite.origAccount);
        assertEquals(-1000, seite.origSignedCents);
        assertEquals(1000L, seite.origCreatedAt);
    }

    /** Frische Umbuchung: ohne Vorlage bleibt die Zeile unberührt (noch nicht exportiert). */
    @Test
    public void ohneVorlageBleibtDieZeileFrisch() {
        Booking seite = new Booking();

        EditStatus.inherit(null, seite);

        assertFalse(seite.edited);
        assertFalse(seite.exported);
    }

    /** Die Signatur zeigt bei „bearbeitet" auf die Fassung in der Datei, sonst auf die aktuelle. */
    @Test
    public void signaturZeigtAufDieFassungInDerDatei() {
        Booking b = exportiert("Girokonto", 400, 2000L);
        assertEquals("Girokonto", EditStatus.fileAccount(b));
        assertEquals(-400, EditStatus.fileSignedCents(b));
        assertEquals(2000L, EditStatus.fileCreatedAt(b));

        b.exported = false;
        b.edited = true;
        b.origAccount = "Bargeld";
        b.origSignedCents = -250;
        b.origCreatedAt = 1000L;
        assertEquals("Bargeld", EditStatus.fileAccount(b));
        assertEquals(-250, EditStatus.fileSignedCents(b));
        assertEquals(1000L, EditStatus.fileCreatedAt(b));
    }
}
