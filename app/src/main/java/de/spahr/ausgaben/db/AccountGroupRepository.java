package de.spahr.ausgaben.db;

import android.os.Handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import de.spahr.ausgaben.db.Repository.Callback;

/**
 * Kontengruppen und Kontenreihenfolge. Kollaborator hinter der {@link Repository}-Fassade; teilt sich
 * deren Executor und Main-Handler, damit die Reihenfolge der Datenbankzugriffe erhalten bleibt.
 *
 * <p>Gruppen mit {@code auto = true} stammen aus der KMyMoney-Datei – aus dem Institutsblock oder aus den
 * bevorzugten Konten. Sie spiegeln nur die Datei: nur der Import schreibt sie, von Hand sind sie weder
 * änderbar noch löschbar.</p>
 */
class AccountGroupRepository {

    private final AccountDao accountDao;
    private final AccountGroupDao groupDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    AccountGroupRepository(AccountDao accountDao, AccountGroupDao groupDao,
                           ExecutorService executor, Handler mainHandler) {
        this.accountDao = accountDao;
        this.groupDao = groupDao;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /**
     * Alle Kontengruppen – Favoriten zuerst, dann selbst angelegte, dann die Bankgruppen. Für das Zuordnungs-Menü am
     * Konto: dort müssen auch Gruppen auftauchen, in denen gerade nur geschlossene Konten stehen, sonst
     * ließen sie sich nie wiederbeleben.
     */
    void getGroups(final Callback<List<AccountGroup>> callback) {
        executor.execute(() -> {
            final List<AccountGroup> result = groupDao.getAll();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Gruppen für die Auswahl – nur solche mit mindestens einem offenen Konto. */
    void getSelectableGroups(final Callback<List<AccountGroup>> callback) {
        executor.execute(() -> {
            final List<AccountGroup> result = groupDao.getSelectable();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Die gewählte Gruppe, sofern sie noch wählbar ist. Liefert {@code null}, wenn es sie nicht mehr gibt
     * oder in ihr kein Konto mehr offen ist – die Ansicht fällt dann auf „Alle Konten" zurück.
     */
    void getGroup(final long groupId, final Callback<AccountGroup> callback) {
        executor.execute(() -> {
            final AccountGroup result = groupId <= 0 ? null : groupDao.getSelectableById(groupId);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Namen aller aktiven Konten einer Gruppe – Grundlage des Mehrkonten-Filters. */
    void getNamesInGroup(final long groupId, final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = groupId <= 0
                    ? new ArrayList<>() : accountDao.getNamesInGroup(groupId);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** {@link #applyMembershipNow} hat alles übernommen. */
    static final int APPLY_OK = 0;
    /**
     * Der Name der neuen Gruppe gehört einer aus der Datei abgeleiteten Gruppe. Es wurde <em>nichts</em>
     * geschrieben – der Dialog bleibt offen und der Nutzer kann den Namen ändern.
     */
    static final int APPLY_NAME_FROM_FILE = 1;

    /**
     * Übernimmt die Gruppenzuordnung eines Kontos in einem Rutsch: {@code selected} ist der vollständige
     * Sollzustand über alle eigenen Gruppen, {@code newGroupName} eine zusätzlich anzulegende. Aus der
     * Datei abgeleitete Gruppen bleiben unberührt – sie spiegeln nur die .kmy.
     */
    void applyMembership(final String account, final Set<Long> selected, final String newGroupName,
                         final Callback<Integer> callback) {
        executor.execute(() -> {
            final int result = applyMembershipNow(account, selected, newGroupName);
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(result));
            }
        });
    }

    /** Der eigentliche Ablauf auf dem Datenbank-Thread; getrennt, damit ihn ein Test direkt aufrufen kann. */
    int applyMembershipNow(String account, Set<Long> selected, String newGroupName) {
        Long accountId = accountDao.getIdByName(account);
        if (accountId == null) {
            return APPLY_OK;
        }
        String neu = newGroupName == null ? "" : newGroupName.trim();
        if (!neu.isEmpty()) {
            Long vorhanden = groupDao.getIdByName(neu);
            AccountGroup belegt = vorhanden == null ? null : groupDao.getById(vorhanden);
            if (belegt != null && belegt.auto) {
                // Vor dem ersten Schreiben prüfen: eine Zuordnung zu einer Datei-Gruppe wäre beim
                // nächsten Import spurlos wieder fort, und halb Übernommenes wäre schwer zu erklären.
                return APPLY_NAME_FROM_FILE;
            }
        }
        Set<Long> gewaehlt = selected == null ? new HashSet<>() : selected;
        for (AccountGroup g : groupDao.getAll()) {
            if (g.auto) {
                continue;
            }
            if (gewaehlt.contains(g.id)) {
                groupDao.addMember(new AccountGroupMember(g.id, accountId));
            } else {
                groupDao.removeMember(g.id, accountId);
            }
        }
        if (!neu.isEmpty()) {
            long id = findOrCreate(neu, false);
            if (id > 0) {
                groupDao.addMember(new AccountGroupMember(id, accountId));
            }
        }
        // Wer sein letztes Konto verloren hat, verschwindet – das ist der einzige Weg, eine eigene
        // Gruppe wieder loszuwerden, seit die Auswahl ein Dropdown ohne langen Tipp ist.
        groupDao.deleteEmpty(false);
        return APPLY_OK;
    }

    /** Gruppen-IDs, in denen das Konto steht. */
    void getGroupIdsOfAccount(final String account, final Callback<Set<Long>> callback) {
        executor.execute(() -> {
            final Set<Long> result = new HashSet<>();
            Long accountId = accountDao.getIdByName(account);
            if (accountId != null) {
                result.addAll(groupDao.getGroupIdsOfAccount(accountId));
            }
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Übernimmt die aus der KMyMoney-Datei abgeleiteten Gruppen: je Bankinstitut eine, dazu die
     * „Favoriten" aus den bevorzugten Konten. Alle tragen {@code auto = true} und ihre Mitglieder werden
     * vollständig neu gesetzt.
     *
     * <p>Beide Quellen laufen bewusst in <em>einem</em> Durchgang: {@code clearAutoMembers()} räumt alle
     * abgeleiteten Zuordnungen auf einmal weg, ein zweiter Aufruf würde also löschen, was der erste
     * gerade geschrieben hat.</p>
     *
     * @param favoritesLabel übersetzter Name der Favoritengruppe; wiedergefunden wird sie am
     *                       Herkunftskennzeichen, nicht am Namen.
     */
    void applyFileGroups(Map<String, String> accountToInstitution, List<String> favorites,
                         String favoritesLabel) {
        // Die Datei gibt die Mitglieder vor, nicht der Altbestand: erst alle Zuordnungen räumen …
        groupDao.clearAutoMembers();
        if (accountToInstitution != null) {
            for (Map.Entry<String, String> e : accountToInstitution.entrySet()) {
                String institution = e.getValue() == null ? "" : e.getValue().trim();
                if (institution.isEmpty()) {
                    continue;
                }
                addToGroup(findOrCreate(institution, true, AccountGroup.SOURCE_BANK), e.getKey());
            }
        }
        if (favorites != null && !favorites.isEmpty()) {
            long groupId = findOrCreateBySourceKey(AccountGroup.SOURCE_FAVORITES, favoritesLabel);
            for (String account : favorites) {
                addToGroup(groupId, account);
            }
        }
        // … danach fallen abgeleitete Gruppen weg, die es in der Datei nicht mehr gibt.
        groupDao.deleteEmpty(true);
    }

    private void addToGroup(long groupId, String account) {
        Long accountId = accountDao.getIdByName(account);
        if (groupId > 0 && accountId != null) {
            groupDao.addMember(new AccountGroupMember(groupId, accountId));
        }
    }

    /**
     * Setzt den Namen der Favoritengruppe auf den Text der aktuellen Sprache. Läuft bei jedem Start, damit
     * die Gruppe nach einem Sprachwechsel nicht auf dem alten Wort sitzenbleibt. Steht dem Namen eine
     * gleichnamige eigene Gruppe im Weg, weicht sie – wie beim Anlegen, siehe {@link #freeName}.
     */
    void renameFavorites(String label) {
        String name = label == null ? "" : label.trim();
        if (name.isEmpty()) {
            return;
        }
        AccountGroup group = groupDao.getBySourceKey(AccountGroup.SOURCE_FAVORITES);
        if (group == null || group.name.equals(name)) {
            return;
        }
        groupDao.setName(group.id, freeName(name, group.id));
    }

    /** Räumt eigene Gruppen weg, die durch das Löschen von Konten leer geworden sind. */
    void deleteEmptyCustomGroups() {
        groupDao.deleteEmpty(false);
    }

    /** Reihenfolge der Kontenarten. */
    void getKindOrder(final Callback<int[]> callback) {
        executor.execute(() -> {
            final int[] result = AccountOrder.kindSequence(groupDao.getKindOrder());
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    void saveKindOrder(final int[] kinds, final Runnable onDone) {
        executor.execute(() -> {
            if (kinds != null) {
                for (int i = 0; i < kinds.length; i++) {
                    groupDao.setKindOrder(new AccountKindOrder(kinds[i], i));
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Schreibt die neue Reihenfolge einer Kontenart; nur tatsächlich verschobene Konten werden geschrieben. */
    void saveAccountOrder(final List<Account> accountsInOrder, final Runnable onDone) {
        executor.execute(() -> {
            for (Account a : AccountOrder.renumber(accountsInOrder)) {
                accountDao.setSortPos(a.id, a.sortPos);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Vorhandene Gruppe gleichen Namens finden oder neu anlegen. Läuft auf dem Datenbank-Thread. */
    private long findOrCreate(String name, boolean auto) {
        return findOrCreate(name, auto, "");
    }

    private long findOrCreate(String name, boolean auto, String sourceKey) {
        Long existing = groupDao.getIdByName(name);
        if (existing != null) {
            return existing;
        }
        long id = groupDao.insert(new AccountGroup(name, auto, sourceKey));
        if (id > 0) {
            return id;
        }
        Long again = groupDao.getIdByName(name); // Wettlauf mit einem gleichnamigen Eintrag
        return again == null ? 0 : again;
    }

    /**
     * Die abgeleitete Gruppe zu ihrem Herkunftskennzeichen finden oder anlegen. Der Name taugt dafür
     * nicht: er ist übersetzt und wechselt mit der Sprache.
     */
    private long findOrCreateBySourceKey(String sourceKey, String label) {
        AccountGroup existing = groupDao.getBySourceKey(sourceKey);
        if (existing != null) {
            return existing.id;
        }
        String name = label == null ? "" : label.trim();
        if (name.isEmpty()) {
            name = sourceKey;
        }
        return groupDao.insert(new AccountGroup(freeName(name, 0), true, sourceKey));
    }

    /**
     * Macht den Namen frei: eine gleichnamige <em>selbst angelegte</em> Gruppe wird gelöscht – wer eine
     * Gruppe „Favoriten" von Hand gepflegt hat, meinte damit dasselbe, und zwei gleichnamige Töpfe wären
     * nur verwirrend; ab jetzt gilt die Datei. Blockiert dagegen eine andere abgeleitete Gruppe den Namen
     * (eine Bank namens „Favoriten"), bleibt sie unangetastet und der Name bekommt eine Ziffer.
     *
     * @param keepId Gruppe, die den Namen behalten darf (0 = noch keine)
     */
    private String freeName(String name, long keepId) {
        Long other = groupDao.getIdByName(name);
        if (other == null || other == keepId) {
            return name;
        }
        groupDao.deleteCustom(other); // greift nur bei auto = 0
        if (groupDao.getIdByName(name) == null) {
            return name;
        }
        for (int n = 2; ; n++) {
            String kandidat = name + " (" + n + ")";
            if (groupDao.getIdByName(kandidat) == null) {
                return kandidat;
            }
        }
    }
}
