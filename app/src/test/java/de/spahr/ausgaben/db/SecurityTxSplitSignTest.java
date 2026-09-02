package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Das Vorzeichen einer Kategoriezeile übersteht den Weg durch die Datenbank — und die Anzeige zeigt es.
 *
 * <p>Bis 1.12 war {@link SecurityTxSplit#amountCents} in der Ablage immer nicht-negativ: die
 * Erfassungsmaske nahm eine Zeile wie „Kapitalertragsteuer −20,00" an und erklärte sie für stimmig
 * ({@code SplitRowController.isValid} summiert vorzeichenbehaftet), schrieb sie dann aber über
 * {@code Math.abs} weg. Aus 100 und −20 (Summe 80) wurden 100 und 20 (Summe 120), und beim nächsten
 * Öffnen stand etwas anderes da, als eingegeben worden war.</p>
 *
 * <p>Geprüft wird hier bewusst die <b>Ablage</b> und nicht die Maske: die Maske lässt sich unter
 * Robolectric nicht aufbauen (ihr Layout scheitert an einer plattforminternen Zeichnung), und die
 * eigentliche Zusicherung — was gespeichert wurde, kommt unverändert zurück — hängt nicht an ihr.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxSplitSignTest {

    private Context ctx;
    private AppDatabase db;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        executor = Executors.newSingleThreadExecutor();
        db = AppDatabase.getInstance(ctx);
        imHintergrund(() -> {
            db.clearAllTables();
            return null;
        });
    }

    @After
    public void tearDown() throws Exception {
        imHintergrund(() -> {
            db.clearAllTables();
            return null;
        });
        executor.shutdownNow();
    }

    private <T> T imHintergrund(Callable<T> arbeit) throws Exception {
        return executor.submit(arbeit).get(10, TimeUnit.SECONDS);
    }

    /**
     * Zinsertrag 100 €, Kapitalertragsteuer −20 €, Gutschrift 80 € — beide Zeilen im selben Topf,
     * wie sie in der Maske untereinander stehen.
     */
    private long legeBewegungMitAbzugszeileAn() throws Exception {
        return imHintergrund(() -> {
            SecurityTx tx = new SecurityTx();
            tx.depot = "Depot A";
            tx.securityKmyId = "S1";
            tx.securityName = "Musterfonds";
            tx.moneyAccount = "Girokonto";
            tx.action = "dividend";
            tx.amountCents = 8_000L;
            tx.netCents = 8_000L;
            tx.date = 0L;
            tx.pending = true;
            long id = db.securityDao().insertTx(tx);
            db.securityDao().insertSplit(teil(id, "Zinsen", 10_000L, 0));
            db.securityDao().insertSplit(teil(id, "Kapitalertragsteuer", -2_000L, 1));
            return id;
        });
    }

    private static SecurityTxSplit teil(long txId, String kategorie, long cents, int sort) {
        SecurityTxSplit s = new SecurityTxSplit();
        s.txId = txId;
        s.category = kategorie;
        s.amountCents = cents;
        s.income = true;
        s.sort = sort;
        return s;
    }

    @Test
    public void eineAbzugszeileKommtNegativZurueck() throws Exception {
        long id = legeBewegungMitAbzugszeileAn();

        List<SecurityTxSplit> teile = imHintergrund(() -> db.securityDao().getSplits(id));

        assertEquals("beide Zeilen sind da", 2, teile.size());
        assertEquals("der Ertrag", 10_000L, teileNach(teile, "Zinsen"));
        assertEquals("der Abzug behält sein Vorzeichen",
                -2_000L, teileNach(teile, "Kapitalertragsteuer"));
    }

    /**
     * Und die Summe der Zeilen ergibt wieder den Betrag, der in der Maske darüber steht. Genau das war
     * kaputt: mit {@code Math.abs} kamen 12.000 statt 8.000 heraus, und die Aufteilung passte nicht
     * mehr zu ihrem eigenen Summenfeld.
     */
    @Test
    public void dieSummeDerZeilenTrifftDenBetragDarueber() throws Exception {
        long id = legeBewegungMitAbzugszeileAn();

        long summe = 0;
        for (SecurityTxSplit teil : imHintergrund(() -> db.securityDao().getSplits(id))) {
            summe += teil.amountCents;
        }

        assertEquals(8_000L, summe);
    }

    /**
     * Beim Zurückschreiben in die Maske wird der Betrag über {@code MoneyFormat.plain} gesetzt
     * ({@code SecurityTxEditActivity.fillSplitRows}). Der Wert muss dort mit Minuszeichen ankommen —
     * sonst stünde in der wieder geöffneten Bewegung eine andere Zahl als gespeichert.
     */
    @Test
    public void dieAnzeigeZeigtDasMinuszeichen() {
        assertEquals("-20,00", MoneyFormat.plain(-2_000L));
        assertEquals("100,00", MoneyFormat.plain(10_000L));
    }

    private static long teileNach(List<SecurityTxSplit> teile, String kategorie) {
        for (SecurityTxSplit teil : teile) {
            if (kategorie.equals(teil.category)) {
                return teil.amountCents;
            }
        }
        throw new AssertionError("Kategorie fehlt: " + kategorie);
    }
}
