package de.spahr.ausgaben.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.spahr.ausgaben.db.Language;
import de.spahr.ausgaben.db.TranslationDao;

/**
 * Der Weg einer hochgeladenen Sprache über ein App-Update hinweg: Vorlage holen, die neu dazugekommenen
 * Texte nachtragen, Datei wieder hochladen.
 *
 * <p>Robolectric, nicht JUnit pur: {@code unitTests.returnDefaultValues} macht {@code org.json} im
 * reinen JVM-Test wirkungslos, und die Vorlage <em>ist</em> JSON.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TranslationRoundTripTest {

    private static TranslationDao.KeyValue kv(String key, String value) {
        TranslationDao.KeyValue out = new TranslationDao.KeyValue();
        out.key = key;
        out.value = value;
        return out;
    }

    /** Deutsch und Englisch als Referenz – drei Schlüssel, einer davon nagelneu. */
    private static List<TranslationDao.KeyValue> de() {
        return Arrays.asList(kv("gruss", "Hallo"), kv("tschuess", "Tschüss"), kv("neu", "Nagelneu"));
    }

    private static List<TranslationDao.KeyValue> en() {
        return Arrays.asList(kv("gruss", "Hello"), kv("tschuess", "Bye"), kv("neu", "Brand new"));
    }

    /** Die hochgeladene Sprache kennt die ersten beiden, den dritten noch nicht. */
    private static List<TranslationDao.KeyValue> katalanisch() {
        return new ArrayList<>(Arrays.asList(kv("gruss", "Hola"), kv("tschuess", "Adéu")));
    }

    private static Language katalanischeZeile() {
        return new Language("ca", "Català", "€", "plain_comma");
    }

    /**
     * Der eigentliche Zweck: die vorhandenen Texte stehen schon drin, nur der neue ist leer – und genau
     * er wird gezählt. Ohne das müßte für einen einzigen Nachtrag alles neu getippt werden.
     */
    @Test
    public void dieVorlageIstVorbelegtUndZaehltNurDasFehlende() throws Exception {
        TranslationIo.Template t =
                TranslationIo.buildTemplate(de(), en(), katalanisch(), katalanischeZeile());

        assertEquals(1, t.missing);
        assertEquals(3, t.total);

        JSONObject root = new JSONObject(t.json);
        assertEquals("ca", root.getString("language"));
        assertEquals("Català", root.getString("displayName"));
        JSONObject strings = root.getJSONObject("strings");
        assertEquals("Hola", strings.getJSONObject("gruss").getString("value"));
        assertEquals("", strings.getJSONObject("neu").getString("value"));
        // Die Referenzspalten bleiben, wozu sie da sind: zum Übersetzen.
        assertEquals("Brand new", strings.getJSONObject("neu").getString("en"));
    }

    /**
     * Bei einer eingebauten Sprache ist die Vorlage der Anfang einer <em>neuen</em> Sprache und bleibt
     * darum leer – auch der Kopf, sonst überschriebe der Übersetzer beim Hochladen versehentlich die
     * Sprache, aus der er abgeschrieben hat.
     */
    @Test
    public void beiEinerEingebautenSpracheBleibtDieVorlageLeer() throws Exception {
        TranslationIo.Template t = TranslationIo.buildTemplate(de(), en(), null, null);

        assertEquals(3, t.missing);
        assertEquals(3, t.total);
        JSONObject root = new JSONObject(t.json);
        assertEquals("", root.getString("language"));
        assertEquals("", root.getJSONObject("strings").getJSONObject("gruss").getString("value"));
    }

    /**
     * Die Regression, um die es geht: ein leer gelassener Text wird nicht mit Englisch gefüllt. Täte er
     * es, gälte er als übersetzt – die zweite Vorlage brächte ihn englisch vorbelegt zurück und die
     * Fehlliste wäre für immer leer.
     */
    @Test
    public void einLeerGelassenerTextBleibtFehlendUndWirdWiederGezaehlt() throws Exception {
        TranslationIo.Template erste =
                TranslationIo.buildTemplate(de(), en(), katalanisch(), katalanischeZeile());

        TranslationIo.Parsed hochgeladen = TranslationIo.parse(erste.json);

        assertEquals("ca", hochgeladen.code);
        assertEquals(2, hochgeladen.values.size());
        assertFalse("der leere Text darf nicht als Übersetzung gespeichert werden",
                hochgeladen.values.containsKey("neu"));

        // Was der Import in die DB schriebe, ist die Grundlage der nächsten Vorlage.
        List<TranslationDao.KeyValue> ausDerDb = new ArrayList<>();
        for (java.util.Map.Entry<String, String> e : hochgeladen.values.entrySet()) {
            ausDerDb.add(kv(e.getKey(), e.getValue()));
        }
        TranslationIo.Template zweite =
                TranslationIo.buildTemplate(de(), en(), ausDerDb, katalanischeZeile());

        assertEquals("der fehlende Text muß wieder gezählt werden", 1, zweite.missing);
        assertEquals("Hola",
                new JSONObject(zweite.json).getJSONObject("strings")
                        .getJSONObject("gruss").getString("value"));
    }

    /** Und wenn nichts mehr fehlt, meldet die Vorlage das auch: die Zählung geht bis null. */
    @Test
    public void eineVollstaendigeSpracheMeldetNullFehlende() throws Exception {
        List<TranslationDao.KeyValue> vollstaendig = katalanisch();
        vollstaendig.add(kv("neu", "Novíssim"));

        TranslationIo.Template t =
                TranslationIo.buildTemplate(de(), en(), vollstaendig, katalanischeZeile());

        assertEquals(0, t.missing);
        assertTrue(TranslationIo.parse(t.json).values.containsKey("neu"));
    }
}
