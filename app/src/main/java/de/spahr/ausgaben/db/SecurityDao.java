package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SecurityDao {

    @Insert
    void insertSecurity(Security security);

    @Insert
    void insertTx(SecurityTx tx);

    @Query("SELECT * FROM security WHERE depot = :depot ORDER BY name COLLATE NOCASE ASC")
    List<Security> getSecurities(String depot);

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

    @Query("DELETE FROM security_tx WHERE depot = :depot")
    void deleteTx(String depot);

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
}
