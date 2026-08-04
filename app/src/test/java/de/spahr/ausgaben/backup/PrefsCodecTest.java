package de.spahr.ausgaben.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Einstellungen typtreu durch JSON und zurück (org.json braucht Robolectric). */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PrefsCodecTest {

    @Test
    public void alleTypenUeberstehenDieRunde() throws Exception {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("url", "smb://host/share");
        in.put("gps", true);
        in.put("night", 2);
        in.put("stamp", 1_700_000_000_000L);
        in.put("factor", 1.15f);
        in.put("pending", new HashSet<>(Arrays.asList("a.jpg", "b.jpg")));

        Map<String, Object> out = PrefsCodec.fromJson(PrefsCodec.toJson(in));

        assertEquals("smb://host/share", out.get("url"));
        assertEquals(Boolean.TRUE, out.get("gps"));
        assertEquals(Integer.valueOf(2), out.get("night"));
        assertEquals(Long.valueOf(1_700_000_000_000L), out.get("stamp"));
        assertEquals(1.15f, (Float) out.get("factor"), 0.0001f);
        @SuppressWarnings("unchecked")
        Set<String> set = (Set<String>) out.get("pending");
        assertEquals(2, set.size());
        assertTrue(set.contains("a.jpg"));
    }

    @Test
    public void unbekannteTypenWerdenAusgelassen() throws Exception {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("gut", "ja");
        in.put("schlecht", new Object());
        Map<String, Object> out = PrefsCodec.fromJson(PrefsCodec.toJson(in));
        assertEquals(1, out.size());
        assertFalse(out.containsKey("schlecht"));
    }

    @Test
    public void leeresJsonErgibtLeereKarte() throws Exception {
        assertTrue(PrefsCodec.fromJson("").isEmpty());
        assertTrue(PrefsCodec.fromJson(null).isEmpty());
    }
}
