package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SecurityDao {

    @Insert
    void insertSecurity(Security security);

    /** Legt eine Bewegung an und liefert ihre id (der Import ignoriert sie, die Erfassung braucht sie). */
    @Insert
    long insertTx(SecurityTx tx);

    @Insert
    void insertPrice(SecurityPrice price);

    @Query("SELECT * FROM security WHERE depot = :depot ORDER BY name COLLATE NOCASE ASC")
    List<Security> getSecurities(String depot);

    /** Alle Wertpapiere aller Depots – Grundlage für die depotübergreifende Zeitreihen-Bewertung. */
    @Query("SELECT * FROM security")
    List<Security> getAllSecurities();

    /**
     * Die Namen aller Wertpapiere. In der KMyMoney-Datei heißt das Unterkonto eines Wertpapiers genauso
     * wie das Wertpapier selbst – daran erkennt der Buchungs-Editor eine Wertpapier-Buchung.
     */
    @Query("SELECT name FROM security")
    List<String> getSecurityNames();

    /** Alle Bewegungen (Depot, Wertpapier, Datum, Stückzahl) über alle Depots, zeitlich sortiert. */
    @Query("SELECT depot, security_kmy_id AS kmyId, date, shares FROM security_tx ORDER BY date ASC")
    List<TxPoint> getAllTxPoints();

    /** Vollständige Kurshistorie über alle Depots, zeitlich sortiert. */
    @Query("SELECT depot, security_kmy_id AS kmyId, date, price FROM security_price ORDER BY date ASC")
    List<PricePoint> getAllPricePoints();

    /** Alle Dividenden-Bewegungen (Brutto {@code amount} + Netto {@code net}) über alle Depots, zeitlich sortiert. */
    @Query("SELECT date, amount_cents AS amount, net_cents AS net FROM security_tx "
            + "WHERE action = 'dividend' ORDER BY date ASC")
    List<DividendPoint> getAllDividendPoints();

    /** Gehaltene Stückzahl je Wertpapier (Summe der Bewegungen). */
    @Query("SELECT security_kmy_id AS kmyId, SUM(shares) AS shares FROM security_tx "
            + "WHERE depot = :depot GROUP BY security_kmy_id")
    List<ShareSum> getShareSums(String depot);

    /** Gehaltene Stückzahl je Wertpapier bis zu einem Stichtag (exklusiv) – für „komplett verkauft am Ende". */
    @Query("SELECT security_kmy_id AS kmyId, SUM(shares) AS shares FROM security_tx "
            + "WHERE depot = :depot AND date < :toMs GROUP BY security_kmy_id")
    List<ShareSum> getShareSumsUntil(String depot, long toMs);

    /** Bewegungen eines Wertpapiers (neueste zuerst). */
    @Query("SELECT * FROM security_tx WHERE depot = :depot AND security_kmy_id = :kmyId "
            + "ORDER BY date DESC, id DESC")
    List<SecurityTx> getTxBySecurity(String depot, String kmyId);

    /** Depot-Namen mit vorhandenen Wertpapieren. */
    @Query("SELECT DISTINCT depot FROM security ORDER BY depot COLLATE NOCASE ASC")
    List<String> distinctDepots();

    /**
     * Beträge je Bewegungsart über das ganze Depot (Brutto + Netto; für Nettoeinsatz/Gewinn). Für
     * Ein-/Ausbuchungen mit manuell gesetztem Wert ({@link SecurityTxValueOverride}) zählt dieser Wert
     * statt der von KMyMoney immer mit 0 importierten Summe (COALESCE greift nur, wenn ein Override
     * existiert – ohne Override bleibt das Ergebnis unverändert).
     */
    @Query("SELECT t.action AS `action`, SUM(COALESCE(o.amount_cents, t.amount_cents)) AS amount, "
            + "SUM(t.net_cents) AS net FROM security_tx t "
            + "LEFT JOIN security_tx_value_override o ON o.depot = t.depot "
            + "AND o.security_kmy_id = t.security_kmy_id AND o.date = t.date AND o.action = t.action "
            + "AND o.shares = t.shares "
            + "WHERE t.depot = :depot GROUP BY t.action")
    List<ActionSum> getActionSums(String depot);

    /** Beträge je Bewegungsart eines einzelnen Wertpapiers (Brutto + Netto); Override wie oben berücksichtigt. */
    @Query("SELECT t.action AS `action`, SUM(COALESCE(o.amount_cents, t.amount_cents)) AS amount, "
            + "SUM(t.net_cents) AS net FROM security_tx t "
            + "LEFT JOIN security_tx_value_override o ON o.depot = t.depot "
            + "AND o.security_kmy_id = t.security_kmy_id AND o.date = t.date AND o.action = t.action "
            + "AND o.shares = t.shares "
            + "WHERE t.depot = :depot AND t.security_kmy_id = :kmyId GROUP BY t.action")
    List<ActionSum> getActionSumsBySecurity(String depot, String kmyId);

    /** Frühester Bewegungszeitpunkt des Depots (ms); {@code null} bei leerem Depot – Untergrenze des Zeitraums. */
    @Query("SELECT MIN(date) FROM security_tx WHERE depot = :depot")
    Long getFirstTxMs(String depot);

    /**
     * Zeitraum-Summen je Wertpapier und Bewegungsart: Netto-Stückzahl (Käufe − als Kauf gebuchte Verkäufe),
     * Brutto- und Netto-Betrag im Fenster {@code [fromMs, toMs)}. Grundlage der zeitraumbezogenen
     * Depot-Auswertung.
     */
    @Query("SELECT t.security_kmy_id AS kmyId, t.action AS `action`, SUM(t.shares) AS shares, "
            + "SUM(COALESCE(o.amount_cents, t.amount_cents)) AS amount, SUM(t.net_cents) AS net "
            + "FROM security_tx t "
            + "LEFT JOIN security_tx_value_override o ON o.depot = t.depot "
            + "AND o.security_kmy_id = t.security_kmy_id AND o.date = t.date AND o.action = t.action "
            + "AND o.shares = t.shares "
            + "WHERE t.depot = :depot AND t.date >= :fromMs AND t.date < :toMs "
            + "GROUP BY t.security_kmy_id, t.action")
    List<PeriodSum> getPeriodSums(String depot, long fromMs, long toMs);

    @Query("DELETE FROM security WHERE depot = :depot")
    void deleteSecurities(String depot);

    /**
     * Verwirft die aus der Datei stammenden Bewegungen eines Depots (der Reimport schreibt sie neu).
     * In der App erfasste, noch nicht exportierte bleiben stehen – sonst wären sie nach dem nächsten
     * Reimport spurlos verschwunden.
     */
    @Query("DELETE FROM security_tx WHERE depot = :depot AND pending = 0")
    void deleteTx(String depot);

    @Query("DELETE FROM security_price WHERE depot = :depot")
    void deletePrices(String depot);

    @Query("SELECT * FROM security_tx WHERE id = :id")
    SecurityTx getTxById(long id);

    @Update
    void updateTx(SecurityTx tx);

    @Query("DELETE FROM security_tx WHERE id = :id")
    void deleteTxById(long id);

    /** In der App erfasste Bewegungen, die noch in die Datei geschrieben werden müssen. */
    @Query("SELECT * FROM security_tx WHERE pending = 1 ORDER BY date ASC, id ASC")
    List<SecurityTx> getPendingTx();

    /** Nach dem Export: die geschriebenen Bewegungen sind keine Vormerkung mehr. */
    @Query("UPDATE security_tx SET pending = 0 WHERE id IN (:ids)")
    void markTxExported(List<Long> ids);

    /**
     * Jüngste Bewegung derselben Art – Vorbelegung von Gegenkonto und Kategorien in der Erfassungsmaske.
     * Nach Art getrennt, weil die Kategorie eines Kaufs (Gebühr) mit der einer Dividende (Ertrag/Steuer)
     * nichts zu tun hat.
     */
    @Query("SELECT * FROM security_tx WHERE depot = :depot AND security_kmy_id = :kmyId "
            + "AND action = :action ORDER BY date DESC, id DESC LIMIT 1")
    SecurityTx getLastByAction(String depot, String kmyId, String action);

    /**
     * Die zuletzt an diesem Wertpapier verwendeten Gebühren-/Steuerkategorien, neueste zuerst – sie
     * stehen in der Auswahlliste als Vorspann ganz oben, wie die Kategorien eines Empfängers.
     */
    @Query("SELECT fee_category FROM security_tx WHERE depot = :depot AND security_kmy_id = :kmyId "
            + "AND fee_category <> '' GROUP BY fee_category ORDER BY MAX(date) DESC LIMIT 5")
    List<String> getUsedFeeCategories(String depot, String kmyId);

    /** Wie oben für die Ertragskategorien der Dividenden. */
    @Query("SELECT income_category FROM security_tx WHERE depot = :depot AND security_kmy_id = :kmyId "
            + "AND income_category <> '' GROUP BY income_category ORDER BY MAX(date) DESC LIMIT 5")
    List<String> getUsedIncomeCategories(String depot, String kmyId);

    /** Jüngste Bewegung mit hinterlegtem Gegenkonto – Rückfall, wenn es die Art noch nie gab. */
    @Query("SELECT * FROM security_tx WHERE depot = :depot AND money_account <> '' "
            + "ORDER BY date DESC, id DESC LIMIT 1")
    SecurityTx getLastWithAccount(String depot);

    /** Setzt/überschreibt den manuellen Wert einer Ein-/Ausbuchung (übersteht einen Depot-Reimport). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertValueOverride(SecurityTxValueOverride override);

    /** Entfernt einen manuell gesetzten Wert wieder (Zeile fällt auf 0/Platzhalter zurück). */
    @Query("DELETE FROM security_tx_value_override WHERE depot = :depot AND security_kmy_id = :kmyId "
            + "AND date = :date AND action = :action AND shares = :shares")
    void deleteValueOverride(String depot, String kmyId, long date, String action, double shares);

    /** Alle manuell gesetzten Werte eines Depots – zum Einblenden in die Bewegungsliste. */
    @Query("SELECT * FROM security_tx_value_override WHERE depot = :depot")
    List<SecurityTxValueOverride> getValueOverrides(String depot);

    /** Projektion für {@link #getShareSums(String)}. */
    class ShareSum {
        public String kmyId;
        public double shares;
    }

    /** Projektion für die Betragssummen je Bewegungsart (Brutto {@code amount} + Netto {@code net}). */
    class ActionSum {
        public String action;
        public long amount;
        public long net;
    }

    /** Projektion für die Zeitraum-Summen je Wertpapier + Bewegungsart. */
    class PeriodSum {
        public String kmyId;
        public String action;
        public double shares;
        public long amount;
        public long net;
    }

    /** Projektion für eine einzelne Depot-Bewegung (Stückzahl-Änderung zu einem Zeitpunkt). */
    class TxPoint {
        public String depot;
        public String kmyId;
        public long date;
        public double shares;
    }

    /** Projektion für einen historischen Kurspunkt. */
    class PricePoint {
        public String depot;
        public String kmyId;
        public long date;
        public double price;
    }

    /** Projektion für eine Dividenden-Bewegung (Brutto {@code amount} + Netto {@code net}). */
    class DividendPoint {
        public long date;
        public long amount;
        public long net;
    }
}
