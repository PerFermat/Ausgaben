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
 * <p>Gruppen mit {@code auto = true} stammen aus dem Institutsblock der KMyMoney-Datei. Sie spiegeln nur
 * die Datei: nur der Import schreibt sie, von Hand sind sie weder änderbar noch löschbar.</p>
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
     * Alle Kontengruppen – selbst angelegte zuerst, dann die Bankgruppen. Für das Zuordnungs-Menü am
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

    /**
     * Legt eine selbst benannte Gruppe an (oder findet die gleichnamige) und ordnet das Konto zu.
     * Meldet die Gruppen-ID, 0 bei leerem Namen oder unbekanntem Konto.
     */
    void createGroupAndAdd(final String name, final String account, final Callback<Long> callback) {
        executor.execute(() -> {
            long id = 0;
            String trimmed = name == null ? "" : name.trim();
            if (!trimmed.isEmpty()) {
                id = findOrCreate(trimmed, false);
                Long accountId = accountDao.getIdByName(account);
                if (id > 0 && accountId != null) {
                    groupDao.addMember(new AccountGroupMember(id, accountId));
                }
            }
            final long result = id;
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(result));
            }
        });
    }

    /** Ordnet ein Konto einer Gruppe zu oder nimmt es heraus. Bankgruppen bleiben unberührt. */
    void setMembership(final String account, final long groupId, final boolean member,
                       final Runnable onDone) {
        executor.execute(() -> {
            AccountGroup group = groupId <= 0 ? null : groupDao.getById(groupId);
            Long accountId = accountDao.getIdByName(account);
            if (group != null && !group.auto && accountId != null) {
                if (member) {
                    groupDao.addMember(new AccountGroupMember(groupId, accountId));
                } else {
                    groupDao.removeMember(groupId, accountId);
                    // War das ihr letztes Konto, verschwindet die Gruppe kommentarlos.
                    groupDao.deleteEmpty(false);
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
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

    /** Löscht eine selbst angelegte Gruppe samt Zuordnungen; Bankgruppen ignoriert die Abfrage. */
    void deleteCustomGroup(final long groupId, final Runnable onDone) {
        executor.execute(() -> {
            groupDao.deleteCustom(groupId);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Übernimmt die Bankinstitute aus der KMyMoney-Datei: je Institut eine Gruppe mit {@code auto = true},
     * deren Mitglieder vollständig neu gesetzt werden. Konten ohne Institut bleiben ohne Bankgruppe.
     */
    void applyInstitutions(Map<String, String> accountToInstitution) {
        if (accountToInstitution == null || accountToInstitution.isEmpty()) {
            return;
        }
        // Die Datei gibt die Mitglieder vor, nicht der Altbestand: erst alle Bankzuordnungen räumen …
        groupDao.clearAutoMembers();
        for (Map.Entry<String, String> e : accountToInstitution.entrySet()) {
            String institution = e.getValue() == null ? "" : e.getValue().trim();
            if (institution.isEmpty()) {
                continue;
            }
            long groupId = findOrCreate(institution, true);
            Long accountId = accountDao.getIdByName(e.getKey());
            if (groupId > 0 && accountId != null) {
                groupDao.addMember(new AccountGroupMember(groupId, accountId));
            }
        }
        // … danach fallen Bankgruppen weg, deren Institut es in der Datei nicht mehr gibt.
        groupDao.deleteEmpty(true);
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
        Long existing = groupDao.getIdByName(name);
        if (existing != null) {
            return existing;
        }
        long id = groupDao.insert(new AccountGroup(name, auto));
        if (id > 0) {
            return id;
        }
        Long again = groupDao.getIdByName(name); // Wettlauf mit einem gleichnamigen Eintrag
        return again == null ? 0 : again;
    }
}
