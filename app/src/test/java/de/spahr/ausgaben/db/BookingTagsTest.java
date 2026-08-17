package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Die Stichwörter einer Buchung ({@link BookingTags}): zusammenfügen, zerlegen, filtern, beschriften.
 */
public class BookingTagsTest {

    @Test
    public void zerlegenUndZusammenfuegen() {
        assertEquals(Arrays.asList("Urlaub", "Bahn"), BookingTags.parse("Urlaub|Bahn"));
        assertEquals("Urlaub|Bahn", BookingTags.join(Arrays.asList("Urlaub", "Bahn")));
    }

    @Test
    public void leereTeileUndRandLeerzeichenFallenWeg() {
        assertEquals(Collections.singletonList("Urlaub"), BookingTags.parse("|  Urlaub  ||"));
        assertEquals("Urlaub", BookingTags.join(Arrays.asList("", "  ", "Urlaub")));
        assertEquals(0, BookingTags.count(null));
        assertEquals(0, BookingTags.count(""));
    }

    @Test
    public void anhaengenNurEinmal() {
        String tags = BookingTags.add("", "Urlaub");
        tags = BookingTags.add(tags, "Bahn");
        assertEquals("Urlaub|Bahn", tags);
        // Zweimal dasselbe – auch anders geschrieben – bleibt einmal.
        assertEquals("Urlaub|Bahn", BookingTags.add(tags, "urlaub"));
        assertEquals("Urlaub|Bahn", BookingTags.add(tags, "  Bahn "));
    }

    @Test
    public void entfernenAchtetNichtAufGrossschreibung() {
        assertEquals("Bahn", BookingTags.remove("Urlaub|Bahn", "URLAUB"));
        // Ein nicht vorhandenes Stichwort lässt den Wert, wie er war.
        assertEquals("Urlaub|Bahn", BookingTags.remove("Urlaub|Bahn", "Essen"));
    }

    @Test
    public void enthaeltIstDerFilter() {
        assertTrue(BookingTags.contains("Urlaub|Bahn", "bahn"));
        assertFalse(BookingTags.contains("Urlaub|Bahn", "Essen"));
        assertFalse(BookingTags.contains("", "Urlaub"));
        // Kein Stichwort gewählt: der Filter lässt alles durch.
        assertTrue(BookingTags.contains("", ""));
        assertTrue(BookingTags.contains(null, null));
    }

    @Test
    public void beschriftungNenntDieNamenUndSonstDieAnzahl() {
        assertEquals("", BookingTags.label("", 30));
        assertEquals("Urlaub, Bahn", BookingTags.label("Urlaub|Bahn", 30));
        // Passen die Namen nicht mehr, tritt die Anzahl an ihre Stelle.
        assertEquals("2", BookingTags.label("Urlaub|Bahn", 5));
    }

    @Test
    public void dasTrennzeichenImNamenWirdZumLeerzeichen() {
        assertEquals("Urlaub Bahn", BookingTags.sanitize("Urlaub|Bahn"));
        // Sonst entstünden aus einem Stichwort unversehens zwei.
        assertEquals(1, BookingTags.count(BookingTags.add("", "Urlaub|Bahn")));
    }
}
