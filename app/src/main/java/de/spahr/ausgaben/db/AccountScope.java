package de.spahr.ausgaben.db;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Die gerade angezeigten Konten – der Maßstab, wenn die App den Empfänger <b>selbst</b> aussucht.
 *
 * <p>Wer in der Ansicht eines Kontos einen reinen Betrag bucht, meint auch einen Empfänger dieses
 * Kontos. Ein Empfänger, den es bisher nur auf einem anderen Konto gab, hat hier nichts zu suchen –
 * selbst wenn er der nächstgelegene ist.</p>
 *
 * <p>Eine leere Auswahl bedeutet <b>keine</b> Einschränkung (Ansicht „Alle Konten" ohne Gruppe, Uhr
 * ohne Kontowahl). Verglichen wird klein geschrieben, wie überall bei Kontonamen.</p>
 *
 * <p>Rein rechnend, ohne Android: die Datenbankabfragen macht der Aufrufer.</p>
 */
public final class AccountScope {

    private AccountScope() {
    }

    /** Auswahl aus mehreren Kontonamen (Kontengruppe); {@code null} und Leerstrings fallen weg. */
    public static Set<String> of(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>();
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                out.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Auswahl aus einem einzelnen Konto; leer oder {@code null} = keine Einschränkung. */
    public static Set<String> of(String name) {
        return name == null || name.trim().isEmpty()
                ? Collections.emptySet()
                : Collections.singleton(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Gehört die Buchung zu einem der angezeigten Konten? */
    public static boolean covers(Set<String> scope, Booking b) {
        return scope == null || scope.isEmpty() || (b != null && contains(scope, b.account));
    }

    /**
     * Gehört der Alias zu einem der angezeigten Konten? Nennt er in {@code account}, {@code fromAccount}
     * oder {@code toAccount} ein Konto, muß <b>eines davon</b> in der Auswahl liegen – bei Umbuchungen
     * zählt jede der beiden Seiten, denn die Buchung erscheint in beiden Konten. Nennt er gar keines,
     * bleibt er: eine Kontobindung, die es nicht gibt, kann nichts widerlegen.
     */
    public static boolean covers(Set<String> scope, PayeeCorrection a) {
        if (scope == null || scope.isEmpty() || a == null) {
            return true;
        }
        boolean genannt = false;
        for (String konto : new String[]{a.account, a.fromAccount, a.toAccount}) {
            if (konto == null || konto.trim().isEmpty()) {
                continue;
            }
            genannt = true;
            if (contains(scope, konto)) {
                return true;
            }
        }
        return !genannt;
    }

    private static boolean contains(Set<String> scope, String konto) {
        return konto != null && scope.contains(konto.trim().toLowerCase(Locale.ROOT));
    }
}
