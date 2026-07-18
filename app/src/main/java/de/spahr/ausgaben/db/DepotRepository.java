package de.spahr.ausgaben.db;

import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import de.spahr.ausgaben.db.Repository.Callback;
import de.spahr.ausgaben.db.Repository.DepotHolding;
import de.spahr.ausgaben.db.Repository.DepotMetrics;

/**
 * Depot (Wertpapiere): Import-Ersetzen, Bestände, Bewegungen und Kennzahlen (Wert, Käufe/Verkäufe/Dividenden,
 * Nettoeinsatz, Gewinn/Verlust). Kollaborator hinter der {@link Repository}-Fassade.
 */
class DepotRepository {

    private final SecurityDao securityDao;
    private final Context appContext;
    private final ExecutorService executor;
    private final Handler mainHandler;

    DepotRepository(SecurityDao securityDao, Context appContext,
                    ExecutorService executor, Handler mainHandler) {
        this.securityDao = securityDao;
        this.appContext = appContext;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /** Ersetzt die Depotdaten (Wertpapiere + Bewegungen) eines Depots. */
    void replaceDepotImport(final String depot, final List<Security> securities,
                            final List<SecurityTx> transactions, final Runnable onDone) {
        executor.execute(() -> {
            securityDao.deleteTx(depot);
            securityDao.deleteSecurities(depot);
            for (Security s : securities) {
                securityDao.insertSecurity(s);
            }
            for (SecurityTx t : transactions) {
                securityDao.insertTx(t);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Depot-Namen mit vorhandenen Wertpapieren. */
    void getDepots(final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = securityDao.distinctDepots();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Bestände eines Depots: je Wertpapier Stückzahl × letzter Kurs = Wert. */
    void getDepotHoldings(final String depot, final Callback<List<DepotHolding>> callback) {
        executor.execute(() -> {
            Map<String, Double> shares = new HashMap<>();
            for (SecurityDao.ShareSum ss : securityDao.getShareSums(depot)) {
                shares.put(ss.kmyId, ss.shares);
            }
            List<DepotHolding> result = new ArrayList<>();
            for (Security s : securityDao.getSecurities(depot)) {
                double q = shares.containsKey(s.kmyId) ? shares.get(s.kmyId) : 0.0;
                long value = Math.round(q * s.price * 100.0);
                result.add(new DepotHolding(s.name, s.symbol, s.kmyId, q, s.price, value));
            }
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Bewegungen eines Wertpapiers (neueste zuerst). */
    void getSecurityTransactions(final String depot, final String kmyId,
                                 final Callback<List<SecurityTx>> callback) {
        executor.execute(() -> {
            final List<SecurityTx> result = securityDao.getTxBySecurity(depot, kmyId);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Kennzahlen für das komplette Depot. */
    void getDepotMetrics(final String depot, final Callback<DepotMetrics> callback) {
        executor.execute(() -> {
            Map<String, Double> shares = new HashMap<>();
            for (SecurityDao.ShareSum ss : securityDao.getShareSums(depot)) {
                shares.put(ss.kmyId, ss.shares);
            }
            long value = 0;
            for (Security s : securityDao.getSecurities(depot)) {
                double q = shares.containsKey(s.kmyId) ? shares.get(s.kmyId) : 0.0;
                value += Math.round(q * s.price * 100.0);
            }
            final DepotMetrics m = metricsFrom(value, securityDao.getActionSums(depot), dividendsGross());
            mainHandler.post(() -> callback.onResult(m));
        });
    }

    /** Kennzahlen für ein einzelnes Wertpapier. */
    void getSecurityMetrics(final String depot, final String kmyId,
                            final Callback<DepotMetrics> callback) {
        executor.execute(() -> {
            double q = 0.0;
            for (SecurityDao.ShareSum ss : securityDao.getShareSums(depot)) {
                if (kmyId != null && kmyId.equals(ss.kmyId)) {
                    q = ss.shares;
                }
            }
            long value = 0;
            for (Security s : securityDao.getSecurities(depot)) {
                if (kmyId != null && kmyId.equals(s.kmyId)) {
                    value = Math.round(q * s.price * 100.0);
                }
            }
            final DepotMetrics m = metricsFrom(value,
                    securityDao.getActionSumsBySecurity(depot, kmyId), dividendsGross());
            mainHandler.post(() -> callback.onResult(m));
        });
    }

    /** Frühester Bewegungszeitpunkt eines Depots (0 wenn leer). */
    void getDepotFirstTx(final String depot, final Callback<Long> callback) {
        executor.execute(() -> {
            final Long first = securityDao.getFirstTxMs(depot);
            final long result = first == null ? 0L : first;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Zeitraumbezogene Auswertungszeilen je Wertpapier. Aktueller Wert = im Zeitraum gekaufte Stücke ×
     * heutiger Kurs (bei vollem Zeitraum stattdessen der aktuelle Depotstand aus Netto-Stück × Kurs).
     */
    void getDepotChartRows(final String depot, final long fromMs, final long toMs,
                           final boolean wholePeriod, final Callback<List<Repository.DepotChartRow>> callback) {
        executor.execute(() -> {
            final boolean gross = dividendsGross();
            // Kurs + Anzeigename je Wertpapier-ID.
            Map<String, Security> secById = new HashMap<>();
            for (Security s : securityDao.getSecurities(depot)) {
                secById.put(s.kmyId, s);
            }
            // Netto-Stück über alles (für den aktuellen Wert bei vollem Zeitraum).
            Map<String, Double> netShares = new HashMap<>();
            for (SecurityDao.ShareSum ss : securityDao.getShareSums(depot)) {
                netShares.put(ss.kmyId, ss.shares);
            }
            // Zeitraum-Summen je Wertpapier zusammenfassen.
            Map<String, double[]> boughtShares = new HashMap<>();   // Stück aus buy/reinvest im Zeitraum
            Map<String, long[]> buyAmt = new HashMap<>();
            Map<String, long[]> sellAmt = new HashMap<>();
            Map<String, long[]> divAmt = new HashMap<>();
            for (SecurityDao.PeriodSum ps : securityDao.getPeriodSums(depot, fromMs, toMs)) {
                String a = ps.action == null ? "" : ps.action;
                if ("buy".equals(a) || "reinvest".equals(a)) {
                    boughtShares.computeIfAbsent(ps.kmyId, k -> new double[1])[0] += ps.shares;
                    buyAmt.computeIfAbsent(ps.kmyId, k -> new long[1])[0] += ps.amount;
                } else if ("sell".equals(a)) {
                    sellAmt.computeIfAbsent(ps.kmyId, k -> new long[1])[0] += ps.amount;
                } else if ("dividend".equals(a)) {
                    divAmt.computeIfAbsent(ps.kmyId, k -> new long[1])[0] += gross ? ps.amount : ps.net;
                }
            }
            List<Repository.DepotChartRow> result = new ArrayList<>();
            for (Security s : securityDao.getSecurities(depot)) {
                String id = s.kmyId;
                long div = divAmt.containsKey(id) ? divAmt.get(id)[0] : 0;
                long buy = buyAmt.containsKey(id) ? buyAmt.get(id)[0] : 0;
                long sell = sellAmt.containsKey(id) ? sellAmt.get(id)[0] : 0;
                double sharesForValue = wholePeriod
                        ? (netShares.containsKey(id) ? netShares.get(id) : 0.0)
                        : (boughtShares.containsKey(id) ? boughtShares.get(id)[0] : 0.0);
                if (sharesForValue < 0) {
                    sharesForValue = 0;   // im Zeitraum netto verkauft → keine aufgebaute Position (kein negativer Wert)
                }
                long value = Math.round(sharesForValue * s.price * 100.0);
                long netDeposits = buy - sell - div;
                result.add(new Repository.DepotChartRow(s.name, value, netDeposits, div, buy));
            }
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    private static DepotMetrics metricsFrom(long valueCents, List<SecurityDao.ActionSum> sums,
                                            boolean grossDividends) {
        long buy = 0, sell = 0, dividend = 0;
        for (SecurityDao.ActionSum s : sums) {
            String a = s.action == null ? "" : s.action;
            if ("buy".equals(a) || "reinvest".equals(a)) {
                buy += s.amount;
            } else if ("sell".equals(a)) {
                sell += s.amount;
            } else if ("dividend".equals(a)) {
                // Brutto = amount (Einnahme-Split), Netto = net (gutgeschriebenes Geld).
                dividend += grossDividends ? s.amount : s.net;
            }
        }
        return new DepotMetrics(valueCents, buy, sell, dividend);
    }

    /** Einstellung „Dividenden brutto anzeigen" (Standard true). Auf dem Executor-Thread gelesen. */
    private boolean dividendsGross() {
        return new de.spahr.ausgaben.settings.SettingsStore(appContext).isDividendGross();
    }
}
