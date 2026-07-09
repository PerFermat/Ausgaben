package de.spahr.ausgaben.db;

import androidx.room.Dao;
import androidx.room.Insert;
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

    /** Bewegungen eines Wertpapiers (neueste zuerst). */
    @Query("SELECT * FROM security_tx WHERE depot = :depot AND security_kmy_id = :kmyId "
            + "ORDER BY date DESC, id DESC")
    List<SecurityTx> getTxBySecurity(String depot, String kmyId);

    /** Depot-Namen mit vorhandenen Wertpapieren. */
    @Query("SELECT DISTINCT depot FROM security ORDER BY depot COLLATE NOCASE ASC")
    List<String> distinctDepots();

    /** Beträge je Bewegungsart über das ganze Depot (Brutto + Netto; für Nettoeinsatz/Gewinn). */
    @Query("SELECT action, SUM(amount_cents) AS amount, SUM(net_cents) AS net FROM security_tx "
            + "WHERE depot = :depot GROUP BY action")
    List<ActionSum> getActionSums(String depot);

    /** Beträge je Bewegungsart eines einzelnen Wertpapiers (Brutto + Netto). */
    @Query("SELECT action, SUM(amount_cents) AS amount, SUM(net_cents) AS net FROM security_tx "
            + "WHERE depot = :depot AND security_kmy_id = :kmyId GROUP BY action")
    List<ActionSum> getActionSumsBySecurity(String depot, String kmyId);

    @Query("DELETE FROM security WHERE depot = :depot")
    void deleteSecurities(String depot);

    @Query("DELETE FROM security_tx WHERE depot = :depot")
    void deleteTx(String depot);

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
}
