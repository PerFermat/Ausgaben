package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.db.ScheduledAdvance;

/**
 * Weiterstellen geplanter Buchungen in der .kmy ({@link KmyExporter#applyScheduleAdvances}). Der XML-Ausschnitt
 * entspricht dem Aufbau einer echten KMyMoney-Datei: {@code postdate} der eingebetteten Transaktion ist die
 * nächste Fälligkeit, {@code lastPayment} die zuletzt gebuchte Zahlung.
 */
public class ScheduleAdvanceTest {

    private static final String XML =
            "<SCHEDULES count=\"2\">\n"
            + " <SCHEDULED_TX type=\"1\" fixed=\"1\" id=\"SCH000056\" endDate=\"\" occurenceMultiplier=\"1\""
            + " startDate=\"2019-01-15\" occurence=\"32\" name=\"Tanken\" lastPayment=\"2026-07-15\">\n"
            + "  <PAYMENTS/>\n"
            + "  <TRANSACTION entrydate=\"\" memo=\"\" id=\"\" commodity=\"EUR\" postdate=\"2026-08-15\">\n"
            + "   <SPLITS><SPLIT value=\"-50/1\" account=\"A000019\"/></SPLITS>\n"
            + "  </TRANSACTION>\n"
            + " </SCHEDULED_TX>\n"
            + " <SCHEDULED_TX type=\"1\" fixed=\"1\" id=\"SCH000057\" endDate=\"\" occurenceMultiplier=\"1\""
            + " startDate=\"2019-01-01\" occurence=\"16384\" name=\"ADAC\" lastPayment=\"2026-01-02\">\n"
            + "  <PAYMENTS/>\n"
            + "  <TRANSACTION entrydate=\"\" memo=\"\" id=\"\" commodity=\"EUR\" postdate=\"2027-01-01\">\n"
            + "   <SPLITS><SPLIT value=\"-90/1\" account=\"A000019\"/></SPLITS>\n"
            + "  </TRANSACTION>\n"
            + " </SCHEDULED_TX>\n"
            + "</SCHEDULES>";

    private static long ms(String date) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).getTime();
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static ScheduledAdvance advance(long id, String kmyId, String from, String next, String paid) {
        ScheduledAdvance a = new ScheduledAdvance(kmyId, ms(from), next == null ? 0 : ms(next),
                paid == null ? 0 : ms(paid), 0);
        a.id = id;
        return a;
    }

    private static String postdateOf(String xml, String schedId) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<SCHEDULED_TX\\b[^>]*\\bid=\"" + schedId + "\"[^>]*>.*?postdate=\"([^\"]*)\"",
                java.util.regex.Pattern.DOTALL).matcher(xml);
        assertTrue("Regel " + schedId + " nicht gefunden", m.find());
        return m.group(1);
    }

    private static String lastPaymentOf(String xml, String schedId) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<SCHEDULED_TX\\b[^>]*\\bid=\"" + schedId + "\"[^>]*\\blastPayment=\"([^\"]*)\"")
                .matcher(xml);
        assertTrue("Regel " + schedId + " nicht gefunden", m.find());
        return m.group(1);
    }

    @Test
    public void executed_movesPostdateAndLastPayment() {
        List<ScheduledAdvance> list = Collections.singletonList(
                advance(1, "SCH000056", "2026-08-15", "2026-09-15", "2026-08-15"));
        KmyExporter.ScheduleResult r = KmyExporter.applyScheduleAdvances(XML, list);

        assertEquals("2026-09-15", postdateOf(r.xml, "SCH000056"));
        assertEquals("2026-08-15", lastPaymentOf(r.xml, "SCH000056"));
        assertEquals(1, r.writtenIds.size());
        // Andere Regeln bleiben unberührt.
        assertEquals("2027-01-01", postdateOf(r.xml, "SCH000057"));
        assertEquals("2026-01-02", lastPaymentOf(r.xml, "SCH000057"));
    }

    @Test
    public void skipped_movesPostdateOnly() {
        List<ScheduledAdvance> list = Collections.singletonList(
                advance(1, "SCH000056", "2026-08-15", "2026-09-15", null));
        KmyExporter.ScheduleResult r = KmyExporter.applyScheduleAdvances(XML, list);

        assertEquals("2026-09-15", postdateOf(r.xml, "SCH000056"));
        assertEquals("2026-07-15", lastPaymentOf(r.xml, "SCH000056"));
    }

    @Test
    public void fileAlreadyFurther_isNotOverwritten() {
        // KMyMoney hat die Regel selbst gebucht: erwarteter Stand (Juli) passt nicht mehr.
        List<ScheduledAdvance> list = Collections.singletonList(
                advance(7, "SCH000056", "2026-07-15", "2026-08-15", "2026-07-15"));
        KmyExporter.ScheduleResult r = KmyExporter.applyScheduleAdvances(XML, list);

        assertEquals("2026-08-15", postdateOf(r.xml, "SCH000056"));
        assertEquals("2026-07-15", lastPaymentOf(r.xml, "SCH000056"));
        assertTrue(r.writtenIds.isEmpty());
        assertEquals(Collections.singletonList(7L), r.resolvedIds); // Vormerkung trotzdem erledigt
    }

    @Test
    public void unknownSchedule_staysQueued() {
        List<ScheduledAdvance> list = Collections.singletonList(
                advance(3, "SCH999999", "2026-08-15", "2026-09-15", null));
        KmyExporter.ScheduleResult r = KmyExporter.applyScheduleAdvances(XML, list);

        assertEquals(XML, r.xml);
        assertTrue(r.resolvedIds.isEmpty());
    }

    @Test
    public void noFurtherDate_clearsPostdate() {
        List<ScheduledAdvance> list = Collections.singletonList(
                advance(4, "SCH000056", "2026-08-15", null, "2026-08-15"));
        KmyExporter.ScheduleResult r = KmyExporter.applyScheduleAdvances(XML, list);

        assertEquals("", postdateOf(r.xml, "SCH000056"));
        assertEquals("2026-08-15", lastPaymentOf(r.xml, "SCH000056"));
    }

    @Test
    public void severalSchedules_areAllApplied() {
        List<ScheduledAdvance> list = new ArrayList<>();
        list.add(advance(1, "SCH000056", "2026-08-15", "2026-09-15", "2026-08-15"));
        list.add(advance(2, "SCH000057", "2027-01-01", "2028-01-01", null));
        KmyExporter.ScheduleResult r = KmyExporter.applyScheduleAdvances(XML, list);

        assertEquals("2026-09-15", postdateOf(r.xml, "SCH000056"));
        assertEquals("2028-01-01", postdateOf(r.xml, "SCH000057"));
        assertEquals(2, r.writtenIds.size());
    }
}
