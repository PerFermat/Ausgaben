package de.spahr.ausgaben.db;

import android.os.Handler;

import java.util.List;
import java.util.concurrent.ExecutorService;

import de.spahr.ausgaben.db.Repository.Callback;
import de.spahr.ausgaben.db.Repository.YearBudget;

/**
 * Budgetplanung: Ist-Summen je Kategorie sowie Soll-Werte (Speichern, Import-Ersetzen, Verlaufsberechnung).
 * Kollaborator hinter der {@link Repository}-Fassade; teilt sich deren Hintergrund-Executor und Main-Handler.
 */
class BudgetRepository {

    private final BookingDao bookingDao;
    private final BudgetDao budgetDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    BudgetRepository(BookingDao bookingDao, BudgetDao budgetDao,
                     ExecutorService executor, Handler mainHandler) {
        this.bookingDao = bookingDao;
        this.budgetDao = budgetDao;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /** Ist-Summen je Kategorie (Einnahme/Ausgabe) im Zeitraum. */
    void getCategoryActuals(final long fromMs, final long toMs,
                            final Callback<List<CategorySum>> callback) {
        executor.execute(() -> {
            final List<CategorySum> result = bookingDao.getCategoryActuals(fromMs, toMs);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    void getBudget(final int year, final Callback<YearBudget> callback) {
        executor.execute(() -> {
            final List<Budget> lines = budgetDao.getForYear(year);
            final String source = budgetDao.sourceForYear(year);
            mainHandler.post(() -> callback.onResult(new YearBudget(source, lines)));
        });
    }

    /** Setzt/ändert einen Soll-Wert manuell (Herkunft = intern). */
    void saveBudgetLine(final int year, final String category, final boolean isIncome,
                        final long amountCents, final Runnable onDone) {
        executor.execute(() -> {
            budgetDao.upsert(new Budget(year, category, isIncome, amountCents, Budget.SOURCE_INTERNAL));
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Ersetzt das Budget eines Jahres komplett (Import/Berechnung). */
    void replaceBudget(final int year, final String source, final List<Budget> lines,
                       final Runnable onDone) {
        executor.execute(() -> {
            budgetDao.deleteYear(year);
            for (Budget b : lines) {
                b.year = year;
                b.source = source;
                budgetDao.upsert(b);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Berechnet das Budget fürs {@code year} aus dem Verlauf: Ist-Summe aller Buchungen VOR dem Jahresbeginn
     * geteilt durch die Anzahl der Jahre mit Daten. Speichert als internes Budget.
     */
    void computeBudgetFromHistory(final int year, final Runnable onDone) {
        executor.execute(() -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.clear();
            c.set(java.util.Calendar.YEAR, year);
            long yearStart = c.getTimeInMillis();
            List<CategorySum> sums = bookingDao.getCategoryActuals(0L, yearStart);
            List<Integer> years = bookingDao.getDataYearsBefore(yearStart);
            int divisor = years == null || years.isEmpty() ? 1 : years.size();
            // Kein Vorjahr vorhanden → Fallback: laufendes Jahr als einzige Datenbasis.
            List<CategorySum> basis = sums;
            if (basis.isEmpty()) {
                long yearEnd;
                java.util.Calendar c2 = java.util.Calendar.getInstance();
                c2.clear();
                c2.set(java.util.Calendar.YEAR, year + 1);
                yearEnd = c2.getTimeInMillis();
                basis = bookingDao.getCategoryActuals(yearStart, yearEnd);
                divisor = 1;
            }
            // Je Kategorie Einnahme-/Ausgabe-Summe getrennt, dann der Typ aus dem Netto: eine Einnahme auf
            // einer Ausgabekategorie (Erstattung) mindert deren Soll, die Kategorie bleibt Ausgabe.
            java.util.Map<String, long[]> perCat = new java.util.HashMap<>();
            for (CategorySum s : basis) {
                if (s.category == null || s.category.isEmpty()) {
                    continue;
                }
                long[] a = perCat.get(s.category);
                if (a == null) {
                    a = new long[2];
                    perCat.put(s.category, a);
                }
                a[s.isIncome ? 0 : 1] += s.total;
            }
            budgetDao.deleteYear(year);
            for (java.util.Map.Entry<String, long[]> e : perCat.entrySet()) {
                long inc = e.getValue()[0];
                long exp = e.getValue()[1];
                boolean isIncome = inc > exp;
                long net = isIncome ? inc - exp : exp - inc;
                if (net <= 0) {
                    continue;
                }
                long soll = Math.round((double) net / divisor);
                if (soll <= 0) {
                    continue;
                }
                budgetDao.upsert(new Budget(year, e.getKey(), isIncome, soll, Budget.SOURCE_INTERNAL));
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }
}
