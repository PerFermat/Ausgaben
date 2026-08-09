package de.spahr.ausgaben.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reihenfolge-Rechnungen für Konten und Kontenarten: welche Kontenart steht wo, wie werden Konten
 * innerhalb ihrer Kontenart sortiert, und wie werden die Sortierplätze nach einem Verschieben wieder
 * lückenlos durchnummeriert.
 *
 * <p>Reine Rechenregeln ohne Android- und Datenbankbezug, damit sie im JVM-Test festgehalten werden
 * können.</p>
 */
public final class AccountOrder {

    private AccountOrder() {
    }

    /**
     * Die Kontenarten in der gespeicherten Reihenfolge. Nicht gespeicherte Kontenarten hängen in ihrer
     * ursprünglichen Reihenfolge hinten an – eine leere Tabelle ergibt also die Vorgabe Anlage,
     * Verbindlichkeit, Depot.
     */
    public static int[] kindSequence(List<AccountKindOrder> stored) {
        Map<Integer, Integer> pos = new HashMap<>();
        if (stored != null) {
            for (AccountKindOrder o : stored) {
                pos.put(o.kind, o.sortPos);
            }
        }
        List<Integer> kinds = new ArrayList<>();
        for (int kind : AccountKind.ALL) {
            kinds.add(kind);
        }
        // Ohne Eintrag zählt die ursprüngliche Reihenfolge, die hinter allen gespeicherten Plätzen liegt.
        kinds.sort(Comparator.comparingInt(k -> pos.containsKey(k) ? pos.get(k) : AccountKind.ALL.length + k));
        int[] out = new int[kinds.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = kinds.get(i);
        }
        return out;
    }

    /** Sortiert Konten innerhalb einer Kontenart: erst der Sortierplatz, bei Gleichstand der Name. */
    public static void sortWithinKind(List<Account> accounts) {
        if (accounts == null) {
            return;
        }
        accounts.sort((a, b) -> {
            if (a.sortPos != b.sortPos) {
                return Integer.compare(a.sortPos, b.sortPos);
            }
            return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
        });
    }

    /**
     * Nummeriert die Sortierplätze einer Kontenart lückenlos ab 1 durch und meldet, welche Konten sich
     * dadurch ändern – nur die müssen geschrieben werden.
     *
     * <p>Ab 1 und nicht ab 0, damit sich ein bewusst sortiertes Konto von der Vorgabe 0 unterscheidet.</p>
     */
    public static List<Account> renumber(List<Account> accountsInOrder) {
        List<Account> changed = new ArrayList<>();
        if (accountsInOrder == null) {
            return changed;
        }
        for (int i = 0; i < accountsInOrder.size(); i++) {
            Account a = accountsInOrder.get(i);
            if (a.sortPos != i + 1) {
                a.sortPos = i + 1;
                changed.add(a);
            }
        }
        return changed;
    }

    /**
     * Kontenreihenfolge der Auswahlfelder: erst die Favoriten, dann die Konten der gewählten
     * Kontengruppe, dann alle übrigen. Innerhalb jedes Blocks bleibt die vorgegebene Reihenfolge
     * stehen – alle drei Listen kommen aus derselben nach Sortierplatz geordneten Abfrage.
     *
     * <p>Jedes Konto steht genau einmal da, und zwar im ersten Block, in den es gehört. Und nur, was
     * auch in {@code names} steht: ein Favorit, der inzwischen geschlossen ist oder die Trägerzeile
     * eines Depots, gehört in kein Auswahlfeld.</p>
     */
    public static List<String> forPicker(List<String> names, List<String> favorites,
                                         List<String> group) {
        List<String> all = names == null ? new ArrayList<>() : names;
        Set<String> bookable = new HashSet<>(all);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (List<String> block : Arrays.asList(favorites, group)) {
            if (block == null) {
                continue;
            }
            for (String name : block) {
                if (bookable.contains(name)) {
                    out.add(name);
                }
            }
        }
        out.addAll(all);
        return new ArrayList<>(out);
    }

    /** Passt der Name auf den Suchbegriff? Teiltreffer an beliebiger Stelle, Groß/Klein egal. */
    public static boolean matches(String name, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
}
