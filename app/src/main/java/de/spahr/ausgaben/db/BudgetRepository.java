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
    private final CategoryTypeDao categoryTypeDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    BudgetRepository(BookingDao bookingDao, BudgetDao budgetDao, CategoryTypeDao categoryTypeDao,
                     ExecutorService executor, Handler mainHandler) {
        this.bookingDao = bookingDao;
        this.budgetDao = budgetDao;
        this.categoryTypeDao = categoryTypeDao;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /** Netto-Zuflüsse je Kategorie (vorzeichenbehaftet) im Zeitraum. */
    void getCategoryActuals(final long fromMs, final long toMs,
                            final Callback<List<CategorySum>> callback) {
        executor.execute(() -> {
            final List<CategorySum> result = bookingDao.getCategoryActuals(fromMs, toMs);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Historische Zahlungs-Verteilung je Kategorie (Tag im Monat bzw. Monat im Jahr) für die Balkenfarbe. */
    void getCategoryTiming(final boolean monthView, final Callback<List<CategoryBucket>> callback) {
        executor.execute(() -> {
            final List<CategoryBucket> result = monthView
                    ? bookingDao.getDayOfMonthHistogram()
                    : bookingDao.getMonthOfYearHistogram();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Kategorie-Pfad → Typ ({@code true} = Einnahme) aus der Datei; verlässliche Budget-Einordnung. */
    void getCategoryTypes(final Callback<java.util.Map<String, Boolean>> callback) {
        executor.execute(() -> {
            final java.util.Map<String, Boolean> map = loadTypeMap();
            mainHandler.post(() -> callback.onResult(map));
        });
    }

    /** Lädt die Kategorie-Typen (nur auf dem Executor-Thread aufrufen). */
    private java.util.Map<String, Boolean> loadTypeMap() {
        java.util.Map<String, Boolean> map = new java.util.HashMap<>();
        for (CategoryType t : categoryTypeDao.getAll()) {
            if (t.category != null && !t.category.isEmpty()) {
                map.put(t.category, t.isIncome);
            }
        }
        return map;
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
            // Je Kategorie den vorzeichenbehafteten Netto-Zufluss summieren; der Typ kommt allein aus der
            // kmy-Datei. Eine Einnahme auf einer Ausgabekategorie (Erstattung) mindert deren Soll, die
            // Kategorie bleibt Ausgabe. Kategorien ohne kmy-Typ werden übersprungen.
            java.util.Map<String, Boolean> typeMap = loadTypeMap();
            java.util.Map<String, Long> perCat = new java.util.HashMap<>();
            for (CategorySum s : basis) {
                if (s.category == null || s.category.isEmpty()) {
                    continue;
                }
                Long v = perCat.get(s.category);
                perCat.put(s.category, (v == null ? 0L : v) + s.total);
            }
            budgetDao.deleteYear(year);
            for (java.util.Map.Entry<String, Long> e : perCat.entrySet()) {
                Boolean isIncome = typeMap.get(e.getKey());
                if (isIncome == null) {
                    continue; // strikt: nur kmy-Kategorien erhalten ein Soll
                }
                long ist = BudgetMath.ist(isIncome, e.getValue());
                if (ist <= 0) {
                    continue; // negativer/0-Netto → kein Soll (Clamp auf 0)
                }
                long soll = Math.round((double) ist / divisor);
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
