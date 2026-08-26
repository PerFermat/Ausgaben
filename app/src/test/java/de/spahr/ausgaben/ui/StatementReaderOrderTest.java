package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Wer gewinnt: der fest programmierte Leser oder die gelernte Vorlage?
 *
 * <p>Beides zusammen, in dieser Rangfolge — der Leser hat Vorrang, weil er die Abrechnung dieser Bank
 * kennt, statt sie sich aus Beschriftungen zu erschließen; die Vorlage trägt nach, was er offen lässt.
 * Verdrängte er sie ganz, verlöre jemand, der sich für seine Bank eine eigene Regel angelegt hat, sie
 * beim nächsten Update ohne Vorwarnung.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementReaderOrderTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    /** Ein ING-Kauf: der Leser findet alles außer der Gebühr, denn eine Provisionszeile gibt es nicht. */
    private static PdfText ingKauf() {
        return text(
                "ING-DiBa AG · 60628 Frankfurt am Main",
                "Wertpapierabrechnung        Kauf aus Sparplan",
                "ISIN (WKN)                  IE00B3RBWM25 (A1JX52)",
                "Nominale                    Stück            6,09607",
                "Kurs                        EUR               164,04",
                "Kurswert                    EUR             1.000,00",
                "Sonderentgelt               EUR                 2,50",
                "Endbetrag zu Ihren Lasten   EUR             1.002,50",
                "Valuta                      19.08.2026");
    }

    @Test
    public void derLeserGewinntUndDieVorlageTraegtNach() {
        StatementTemplates store = new StatementTemplates(ctx);
        store.clearAll();
        // Eine von Hand angelegte Regel für genau das, was der Leser nicht kennt: das «Sonderentgelt».
        // Dazu eine Netto-Regel, die auf den Kurswert zeigt – dort muss der Leser sich durchsetzen.
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.FEE, regel("Sonderentgelt"));
        rules.put(StatementTemplate.Field.NET, regel("Kurswert"));
        store.save(new StatementTemplate("buy", rules));

        StatementTemplate.Extraction e = StatementImport.extract(store, ingKauf());

        // Die Vorlage trägt nach, was der Leser offen ließ.
        assertEquals(Long.valueOf(250L), e.feeCents);
        // Wo beide etwas wissen, gilt der Leser: der Endbetrag, nicht der Kurswert.
        assertEquals(Long.valueOf(100_250L), e.netCents);
        assertEquals("buy", e.action);
        assertEquals(6.09607, e.shares, 1e-9);
    }

    /** Ohne Leser bleibt alles beim Alten: die gelernte Vorlage allein. */
    @Test
    public void ohneLeserZaehltNurDieVorlage() {
        StatementTemplates store = new StatementTemplates(ctx);
        store.clearAll();
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET, regel("Total amount"));
        store.save(new StatementTemplate("buy", rules));
        PdfText fremd = text(
                "Some Foreign Broker Ltd.",
                "Contract Note",
                "Quantity                    12",
                "Total amount                EUR               500,00");

        StatementTemplate.Extraction e = StatementImport.extract(store, fremd);

        assertEquals(Long.valueOf(50_000L), e.netCents);
        assertNull("kein Leser darf sich hier zuständig fühlen", e.grossCents);
    }

    private static AnchorRule regel(String anchor) {
        return new AnchorRule(Arrays.asList(anchor), AnchorRule.Direction.SAME_LINE, false);
    }

    /** Zeilen zu {@link PdfText}; die Spalte im String wird zur x-Position. */
    private static PdfText text(String... lines) {
        PdfText.Builder b = new PdfText.Builder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) == ' ') {
                    col++;
                    continue;
                }
                int end = col;
                while (end < line.length() && line.charAt(end) != ' ') {
                    end++;
                }
                b.add(0, line.substring(col, end), col * 6f, end * 6f, 14f * (i + 1));
                col = end;
            }
        }
        return b.build();
    }
}
