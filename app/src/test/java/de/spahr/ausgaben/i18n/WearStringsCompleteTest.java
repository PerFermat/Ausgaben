package de.spahr.ausgaben.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nagelt fest, daß {@link LocaleManager#WEAR} jeden {@code wear_*}-Schlüssel der Uhr kennt.
 *
 * <p>Hintergrund: Die Uhr liest ihre Texte nicht aus den eigenen Ressourcen, sondern aus der Map, die
 * das Phone ihr schickt ({@code WearStrings}/{@code WearLocaleWrapper}). Gefüllt wird die aus der
 * Datenbank, und die wiederum allein aus {@code WEAR}. Ein Schlüssel, der nur in
 * {@code wear/res/values/strings.xml} steht, fällt auf der Uhr also still auf die gebündelte
 * DE/EN/ES-Fassung zurück – eine hochgeladene Sprache kann ihn nie übersetzen. Genau so waren sechs
 * Texte über mehrere Versionen hinweg unübersetzbar, ohne daß es auffiel.
 *
 * <p>Reiner JVM-Test: die Wear-Ressourcen gehören einem anderen Gradle-Modul und stehen dem App-Modul
 * nicht als {@code R}-Klasse zur Verfügung, deshalb wird die XML-Datei direkt gelesen.
 */
public class WearStringsCompleteTest {

    private static final Pattern NAME = Pattern.compile("<string\\s+name=\"([^\"]+)\"");

    /** Schlüssel der Uhr-Ressource, die überhaupt übertragen werden können (Präfix {@code wear_}). */
    private static Set<String> wearResourceKeys() throws Exception {
        File xml = new File("../wear/src/main/res/values/strings.xml");
        assertTrue("Wear-strings.xml nicht gefunden: " + xml.getAbsolutePath(), xml.isFile());
        String content = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = NAME.matcher(content);
        while (m.find()) {
            if (m.group(1).startsWith("wear_")) {
                keys.add(m.group(1));
            }
        }
        assertTrue("Keine wear_-Schlüssel gelesen – Regex oder Datei kaputt?", keys.size() > 5);
        return keys;
    }

    private static Set<String> tableKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String[] row : LocaleManager.WEAR) {
            keys.add(row[0]);
        }
        return keys;
    }

    @Test
    public void jederWearSchluesselStehtInDerTabelle() throws Exception {
        Set<String> fehlend = new LinkedHashSet<>(wearResourceKeys());
        fehlend.removeAll(tableKeys());
        assertEquals("In LocaleManager.WEAR nachtragen (sonst auf der Uhr nicht übersetzbar)",
                "[]", fehlend.toString());
    }

    @Test
    public void keinVerwaisterSchluesselInDerTabelle() throws Exception {
        Set<String> verwaist = new LinkedHashSet<>(tableKeys());
        verwaist.removeAll(wearResourceKeys());
        assertEquals("Steht in LocaleManager.WEAR, aber in keiner Wear-Ressource mehr",
                "[]", verwaist.toString());
    }

    /** Jede Zeile trägt Schlüssel + drei Übersetzungen, keine davon leer. */
    @Test
    public void jedeZeileIstVollstaendig() {
        for (String[] row : LocaleManager.WEAR) {
            assertEquals("Zeile " + row[0] + " hat nicht 4 Spalten", 4, row.length);
            for (int i = 1; i < row.length; i++) {
                assertTrue("Leere Übersetzung in Zeile " + row[0] + ", Spalte " + i,
                        row[i] != null && !row[i].trim().isEmpty());
            }
        }
    }
}
