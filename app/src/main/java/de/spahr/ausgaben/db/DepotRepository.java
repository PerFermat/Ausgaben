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

    private final AppDatabase db;
    private final SecurityDao securityDao;
    private final Context appContext;
    private final ExecutorService executor;
    private final Handler mainHandler;

    DepotRepository(AppDatabase db, SecurityDao securityDao, Context appContext,
                    ExecutorService executor, Handler mainHandler) {
        this.db = db;
        this.securityDao = securityDao;
        this.appContext = appContext;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /** Ersetzt die Depotdaten (Wertpapiere + Bewegungen + Kurshistorie) eines Depots. */
    void replaceDepotImport(final String depot, final List<Security> securities,
                            final List<SecurityTx> transactions, final List<SecurityPrice> prices,
                            final Runnable onDone) {
        replaceDepotImport(depot, securities, transactions, prices, null, onDone);
    }

    /**
     * Wie oben, meldet aber den Fortschritt (geschriebene von insgesamt zu schreibenden Zeilen).
     *
     * <p>Der ganze Block läuft in <b>einer</b> Transaktion: vorher bekam jede einzelne Kurszeile ihre
     * eigene (inkl. fsync) – seit der Kurshistorie sind das mehrere tausend, und genau das war die
     * lange Pause in der Anzeige.</p>
     */
    void replaceDepotImport(final String depot, final List<Security> securities,
                            final List<SecurityTx> transactions, final List<SecurityPrice> prices,
                            final de.spahr.ausgaben.util.ProgressListener listener,
                            final Runnable onDone) {
        executor.execute(() -> {
            final int total = securities.size() + transactions.size()
                    + (prices == null ? 0 : prices.size());
            final int[] done = new int[1];
            db.runInTransaction(() -> {
                securityDao.deleteTx(depot);
                securityDao.deleteSecurities(depot);
                securityDao.deletePrices(depot);
                for (Security s : securities) {
                    securityDao.insertSecurity(s);
                    report(listener, done, total);
                }
                for (SecurityTx t : transactions) {
                    securityDao.insertTx(t);
                    report(listener, done, total);
                }
                if (prices != null) {
                    for (SecurityPrice p : prices) {
                        securityDao.insertPrice(p);
                        report(listener, done, total);
                    }
                }
            });
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    private static void report(de.spahr.ausgaben.util.ProgressListener listener, int[] done, int total) {
        done[0]++;
        if (listener != null) {
            listener.onProgress(done[0], total);
        }
    }

    /** Depotübergreifende Bewertung (Zeitreihe) für die Vermögensgrafik. */
    void getDepotValuation(final Callback<DepotValuation> callback) {
        executor.execute(() -> {
            final DepotValuation v = DepotValuation.build(
                    securityDao.getAllSecurities(),
                    securityDao.getAllTxPoints(),
                    securityDao.getAllPricePoints(),
                    securityDao.getAllDividendPoints());
            mainHandler.post(() -> callback.onResult(v));
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

    /** Bewegungen eines Wertpapiers (neueste zuerst); manuell gesetzte Werte für Ein-/Ausbuchungen eingeblendet. */
    void getSecurityTransactions(final String depot, final String kmyId,
                                 final Callback<List<SecurityTx>> callback) {
        executor.execute(() -> {
            final List<SecurityTx> result = securityDao.getTxBySecurity(depot, kmyId);
            applyValueOverrides(depot, result);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Überschreibt amountCents/netCents der übergebenen Zeilen mit einem passenden manuellen Wert (falls vorhanden). */
    private void applyValueOverrides(String depot, List<SecurityTx> rows) {
        List<SecurityTxValueOverride> overrides = securityDao.getValueOverrides(depot);
        if (overrides.isEmpty()) {
            return;
        }
        for (SecurityTx tx : rows) {
            for (SecurityTxValueOverride o : overrides) {
                if (o.securityKmyId.equals(tx.securityKmyId) && o.date == tx.date
                        && o.action.equals(tx.action) && o.shares == tx.shares) {
                    tx.amountCents = o.amountCents;
                    tx.netCents = o.amountCents;
                    break;
                }
            }
        }
    }

    /**
     * Legt eine in der App erfasste Bewegung samt ihrer Geldbuchung an – beides in <b>einer</b> Transaktion,
     * damit nie eine Bewegung ohne Buchung (oder umgekehrt) übrig bleibt. Die Bewegung ist vorgemerkt
     * ({@code pending}) und wird beim nächsten Export in die KMyMoney-Datei geschrieben.
     *
     * @param booking die Geldbuchung; ihre id landet in {@code tx.bookingId}. {@code null} = keine
     *                (dann bleibt der Kontostand unberührt)
     */
    void saveManualTx(final SecurityTx tx, final Booking booking, final Runnable onDone) {
        executor.execute(() -> {
            db.runInTransaction(() -> {
                tx.pending = true;
                if (booking != null) {
                    db.accountDao().insertIfAbsent(new Account(booking.account));
                    tx.bookingId = db.bookingDao().insert(booking);
                }
                securityDao.insertTx(tx);
            });
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Ändert eine noch nicht exportierte Bewegung samt Geldbuchung. Exportierte werden hier nicht
     * angefasst – die stehen bereits in der Datei und gehören dort korrigiert.
     */
    void updateManualTx(final SecurityTx tx, final Booking booking, final Runnable onDone) {
        executor.execute(() -> {
            db.runInTransaction(() -> {
                if (booking != null && tx.bookingId > 0) {
                    booking.id = tx.bookingId;
                    db.accountDao().insertIfAbsent(new Account(booking.account));
                    db.bookingDao().update(booking);
                }
                securityDao.updateTx(tx);
            });
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Entfernt eine noch nicht exportierte Bewegung und die zugehörige Geldbuchung. */
    void deleteManualTx(final long txId, final Runnable onDone) {
        executor.execute(() -> {
            db.runInTransaction(() -> {
                SecurityTx tx = securityDao.getTxById(txId);
                if (tx == null || !tx.pending) {
                    return;   // aus der Datei importiert: dort wird nicht gelöscht
                }
                if (tx.bookingId > 0) {
                    db.bookingDao().delete(tx.bookingId);
                }
                securityDao.deleteTxById(txId);
            });
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Eine einzelne Bewegung (Erfassungsmaske im Ansehen-/Ändern-Modus). */
    void getSecurityTx(final long id, final Callback<SecurityTx> callback) {
        executor.execute(() -> {
            final SecurityTx tx = securityDao.getTxById(id);
            mainHandler.post(() -> callback.onResult(tx));
        });
    }

    /**
     * Gegenkonto und Kategorien der jüngsten Bewegung derselben Art – Vorbelegung der Erfassungsmaske.
     * Gibt es die Art an diesem Wertpapier noch nicht, dient die jüngste Bewegung mit Gegenkonto als
     * Rückfall (dann stimmt wenigstens das Konto).
     */
    void getTxDefaults(final String depot, final String kmyId, final String action,
                       final Callback<SecurityTx> callback) {
        executor.execute(() -> {
            // Von speziell nach allgemein: dasselbe Wertpapier, dann ein beliebiges desselben Depots,
            // zuletzt eines aus einem anderen Depot – immer aber dieselbe Art. Jedes Feld wird einzeln
            // aufgefüllt, denn die speziellere Bewegung kann Konto und Kategorie unterschiedlich gepflegt
            // haben (etwa eine importierte Dividende ohne Ertragskategorie).
            SecurityTx result = new SecurityTx();
            fillFrom(result, securityDao.getLastByAction(depot, kmyId, action));
            fillFrom(result, securityDao.getLastByActionInDepot(depot, action));
            fillFrom(result, securityDao.getLastByActionAnywhere(action));
            final SecurityTx out = result;
            mainHandler.post(() -> callback.onResult(out));
        });
    }

    /** Ergänzt nur, was noch leer ist – die speziellere Quelle kommt zuerst und behält damit Vorrang. */
    private static void fillFrom(SecurityTx target, SecurityTx source) {
        if (source == null) {
            return;
        }
        if (target.moneyAccount.isEmpty()) {
            target.moneyAccount = source.moneyAccount;
        }
        if (target.feeCategory.isEmpty()) {
            target.feeCategory = source.feeCategory;
        }
        if (target.incomeCategory.isEmpty()) {
            target.incomeCategory = source.incomeCategory;
        }
    }

    /**
     * Alle Wertpapiere aller Depots, nach Namen sortiert — für die Auswahl, wenn eine eingelesene
     * Abrechnung eine ISIN trägt, die noch keinem Wertpapier zugeordnet ist.
     */
    void getAllSecurities(final Callback<List<Security>> callback) {
        executor.execute(() -> {
            final List<Security> all = new ArrayList<>(securityDao.getAllSecurities());
            java.util.Collections.sort(all, (a, b) ->
                    a.name.compareToIgnoreCase(b.name));
            mainHandler.post(() -> callback.onResult(all));
        });
    }

    /** Wertpapier zu einer ISIN (aus KMyMoney importiert); {@code null}, wenn keines passt. */
    void getSecurityByIsin(final String isin, final Callback<Security> callback) {
        executor.execute(() -> {
            final Security s = isin == null || isin.trim().isEmpty()
                    ? null : securityDao.getByIsin(isin.trim());
            mainHandler.post(() -> callback.onResult(s));
        });
    }

    /** Die an diesem Wertpapier schon verwendeten Kategorien: {Gebühr/Steuer, Ertrag}. */
    void getUsedCategories(final String depot, final String kmyId,
                           final Callback<List<List<String>>> callback) {
        executor.execute(() -> {
            final List<List<String>> result = new ArrayList<>();
            result.add(securityDao.getUsedFeeCategories(depot, kmyId));
            result.add(securityDao.getUsedIncomeCategories(depot, kmyId));
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Setzt den manuellen Wert einer Ein-/Ausbuchung (übersteht einen Depot-Reimport). */
    void saveSecurityTxValue(final String depot, final String kmyId, final long date, final String action,
                             final double shares, final long amountCents, final Runnable onDone) {
        executor.execute(() -> {
            securityDao.upsertValueOverride(
                    new SecurityTxValueOverride(depot, kmyId, date, action, shares, amountCents));
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Entfernt einen zuvor manuell gesetzten Wert wieder. */
    void clearSecurityTxValue(final String depot, final String kmyId, final long date, final String action,
                              final double shares, final Runnable onDone) {
        executor.execute(() -> {
            securityDao.deleteValueOverride(depot, kmyId, date, action, shares);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
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
            // Netto-Stück bis zum Zeitraumende (exklusiv) – für „komplett verkauft am Ende des Zeitraums".
            Map<String, Double> netSharesAtEnd = new HashMap<>();
            for (SecurityDao.ShareSum ss : securityDao.getShareSumsUntil(depot, toMs)) {
                netSharesAtEnd.put(ss.kmyId, ss.shares);
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
                } else if ("add".equals(a)) {
                    // Einbuchung: nur der manuell gesetzte Geldwert zählt zum Einstandspreis, die
                    // Stückzahl bleibt bewusst außen vor (läuft bereits korrekt über netShares/-AtEnd).
                    buyAmt.computeIfAbsent(ps.kmyId, k -> new long[1])[0] += ps.amount;
                } else if ("sell".equals(a) || "remove".equals(a)) {
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
                // Komplett verkauft = Netto-Bestand am Ende des Zeitraums ~ 0 (spätere Verkäufe zählen noch nicht).
                double netHeldAtEnd = netSharesAtEnd.containsKey(id) ? netSharesAtEnd.get(id) : 0.0;
                boolean fullySold = Math.abs(netHeldAtEnd) < 1e-6;
                result.add(new Repository.DepotChartRow(s.name, value, netDeposits, div, buy, fullySold));
            }
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    private static DepotMetrics metricsFrom(long valueCents, List<SecurityDao.ActionSum> sums,
                                            boolean grossDividends) {
        long buy = 0, sell = 0, dividend = 0;
        for (SecurityDao.ActionSum s : sums) {
            String a = s.action == null ? "" : s.action;
            // Einbuchung = wie Kauf, Ausbuchung = wie Verkauf – zählt nur, wenn ein manueller Wert
            // gesetzt wurde (KMyMoney liefert dafür sonst immer 0, siehe getActionSums/-BySecurity).
            if ("buy".equals(a) || "reinvest".equals(a) || "add".equals(a)) {
                buy += s.amount;
            } else if ("sell".equals(a) || "remove".equals(a)) {
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
