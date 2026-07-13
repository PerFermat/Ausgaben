package de.spahr.ausgaben.db;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kapselt den Datenbankzugriff. Alle Operationen laufen auf einem Hintergrund-Thread;
 * Ergebnisse werden über {@link Callback} auf dem Main-Thread zurückgegeben.
 */
public class Repository {

    public interface Callback<T> {
        void onResult(T result);
    }

    private final BookingDao bookingDao;
    private final AccountDao accountDao;
    private final PayeeDao payeeDao;
    private final PlaceEntryDao placeEntryDao;
    private final PayeeCorrectionDao correctionDao;
    private final TranslationDao translationDao;
    private final SecurityDao securityDao;
    private final BudgetDao budgetDao;
    private final CategoryTypeDao categoryTypeDao;
    private final ScheduledTransactionDao scheduledTransactionDao;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Fokussierte Kollaboratoren; teilen sich Executor + Main-Handler dieser Fassade (Reihenfolge bleibt). */
    private final BudgetRepository budgetRepo;
    private final DepotRepository depotRepo;
    private final AliasResolver aliasResolver;

    public Repository(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        this.bookingDao = db.bookingDao();
        this.accountDao = db.accountDao();
        this.payeeDao = db.payeeDao();
        this.placeEntryDao = db.placeEntryDao();
        this.correctionDao = db.payeeCorrectionDao();
        this.translationDao = db.translationDao();
        this.securityDao = db.securityDao();
        this.budgetDao = db.budgetDao();
        this.categoryTypeDao = db.categoryTypeDao();
        this.scheduledTransactionDao = db.scheduledTransactionDao();
        this.budgetRepo = new BudgetRepository(bookingDao, budgetDao, categoryTypeDao, executor, mainHandler);
        this.depotRepo = new DepotRepository(securityDao, appContext, executor, mainHandler);
        this.aliasResolver = new AliasResolver(bookingDao, correctionDao, accountDao, executor, mainHandler);
    }

    // ---- Mehrsprachigkeit ----

    /** Setzt das Währungskennzeichen eines Kontos (legt es bei Bedarf an). Leere Währung wird ignoriert. */
    public void setAccountCurrency(final String account, final String currency) {
        if (account == null || account.trim().isEmpty() || currency == null || currency.trim().isEmpty()) {
            return;
        }
        executor.execute(() -> {
            accountDao.insertIfAbsent(new Account(account.trim()));
            accountDao.setCurrency(account.trim(), currency.trim());
        });
    }

    /** KMyMoney-Kontotyp beim Import übernehmen (Trennung Anlage/Verbindlichkeit). Typ 0 = ignorieren. */
    public void setAccountType(final String account, final int type) {
        if (account == null || account.trim().isEmpty() || type == 0) {
            return;
        }
        executor.execute(() -> {
            accountDao.insertIfAbsent(new Account(account.trim()));
            accountDao.setType(account.trim(), type);
        });
    }

    /**
     * Klassifiziert beim Import <b>alle</b> bereits vorhandenen Konten neu (Name → KMyMoney-Typ). Nur ein
     * reines UPDATE – Konten, die (noch) nicht existieren, werden nicht angelegt. Typ 0 wird übersprungen.
     */
    public void applyAccountTypes(final java.util.Map<String, Integer> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            for (java.util.Map.Entry<String, Integer> e : types.entrySet()) {
                if (e.getKey() != null && !e.getKey().trim().isEmpty()
                        && e.getValue() != null && e.getValue() != 0) {
                    accountDao.setType(e.getKey().trim(), e.getValue());
                }
            }
        });
    }

    /**
     * Übernimmt beim .kmy-Import den Typ <b>aller</b> Kategorien der Datei (Pfad → Einnahme/Ausgabe).
     * Verlässliche, einzige Typ-Quelle für die Budget-Einordnung. Reines Upsert (mergt, löscht nichts).
     */
    public void applyCategoryTypes(final java.util.Map<String, Boolean> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            for (java.util.Map.Entry<String, Boolean> e : types.entrySet()) {
                if (e.getKey() != null && !e.getKey().trim().isEmpty() && e.getValue() != null) {
                    categoryTypeDao.upsert(new CategoryType(e.getKey().trim(), e.getValue()));
                }
            }
        });
    }

    /**
     * Ersetzt beim .kmy-Import die geplanten Buchungen komplett (leeren + neu einfügen), damit sie sich
     * „sobald ein Konto neu eingelesen wurde" aktualisieren. {@code onDone} optional (Main-Thread).
     */
    public void applyScheduledTransactions(final List<ScheduledTransaction> list, final Runnable onDone) {
        executor.execute(() -> {
            scheduledTransactionDao.deleteAll();
            if (list != null) {
                for (ScheduledTransaction st : list) {
                    if (st != null) {
                        scheduledTransactionDao.insert(st);
                    }
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Geplante Buchungen nach nächster Fälligkeit (für die Seite „Geplante Buchungen"). */
    public void getScheduledTransactions(final Callback<List<ScheduledTransaction>> callback) {
        executor.execute(() -> {
            final List<ScheduledTransaction> result = scheduledTransactionDao.getAllByDue();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Aktive Konten in Anlage/Verbindlichkeit getrennt – für Schublade und Bestände. */
    public void getAccountsGrouped(final Callback<AccountGroups> callback) {
        executor.execute(() -> {
            final AccountGroups g = new AccountGroups(accountDao.getAssetNames(), accountDao.getLiabilityNames());
            mainHandler.post(() -> callback.onResult(g));
        });
    }

    /** Aktive Konten nach Typ getrennt. */
    public static final class AccountGroups {
        public final List<String> assets;
        public final List<String> liabilities;
        public AccountGroups(List<String> assets, List<String> liabilities) {
            this.assets = assets;
            this.liabilities = liabilities;
        }
    }

    public void getLanguages(final Callback<List<Language>> callback) {
        executor.execute(() -> {
            final List<Language> result = translationDao.getLanguages();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Baut die JSON-Export-Vorlage (alle Schlüssel mit DE/EN + leerem „value"). */
    public void buildLanguageTemplate(final Callback<String> callback) {
        executor.execute(() -> {
            String json;
            try {
                json = de.spahr.ausgaben.i18n.TranslationIo.buildTemplate(
                        translationDao.getPairsOrdered("de"), translationDao.getPairsOrdered("en"));
            } catch (Exception e) {
                json = null;
            }
            final String result = json;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Importiert eine (geparste) Sprache in die DB; ersetzt eine bestehende gleichen Codes. */
    public void importLanguage(final de.spahr.ausgaben.i18n.TranslationIo.Parsed parsed,
                               final Runnable onDone) {
        executor.execute(() -> {
            List<Translation> rows = new ArrayList<>();
            for (Map.Entry<String, String> e : parsed.values.entrySet()) {
                rows.add(new Translation(parsed.code, e.getKey(), e.getValue()));
            }
            translationDao.deleteTranslations(parsed.code);
            translationDao.insertAll(rows);
            translationDao.upsertLanguage(new Language(parsed.code, parsed.name));
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Wear-relevante Texte (Schlüssel „wear_*") der Sprache – für die Übertragung an die Uhr. */
    public void getWearStrings(final String lang, final Callback<Map<String, String>> callback) {
        executor.execute(() -> {
            Map<String, String> m = new HashMap<>();
            for (TranslationDao.KeyValue kv : translationDao.getPairs(lang)) {
                if (kv.key.startsWith("wear_")) {
                    m.put(kv.key, kv.value);
                }
            }
            final Map<String, String> result = m;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Speichert eine Buchung und legt Konto/Empfänger bei Bedarf als Auswahlwert an.
     */
    public void saveBooking(final Booking booking, final Runnable onDone) {
        executor.execute(() -> {
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            bookingDao.insert(booking);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Aktualisiert eine Buchung und ergänzt geänderte Konto-/Empfängerwerte. */
    public void updateBooking(final Booking booking, final Runnable onDone) {
        executor.execute(() -> {
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            bookingDao.update(booking);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Aktualisiert eine Buchung und ersetzt ihre Kategorie-Teile (Splitbuchung). {@code parts} leer/null →
     * die Buchung wird zur Einzelkategorie ({@link Booking#category}); vorhandene Teile werden entfernt.
     */
    public void updateSplitBooking(final Booking booking, final List<BookingSplit> parts,
                                   final Runnable onDone) {
        executor.execute(() -> {
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            bookingDao.update(booking);
            bookingDao.deleteSplits(booking.id);
            if (parts != null) {
                for (BookingSplit p : parts) {
                    p.bookingId = booking.id;
                    bookingDao.insertSplit(p);
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Speichert eine neue Splitbuchung (Buchung + Kategorie-Teile) und verschiebt zusätzlich optional den
     * Saldo eines Ortes (wie {@link #saveBookingWithPlace}). {@code parts} sind die Kategorie-Teile.
     */
    public void saveSplitBooking(final Booking booking, final List<BookingSplit> parts,
                                 final String place, final Runnable onDone) {
        booking.place = isRealPlace(place) ? place.trim() : "";
        booking.placeManaged = true;
        executor.execute(() -> {
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            long id = bookingDao.insert(booking);
            if (parts != null) {
                for (BookingSplit p : parts) {
                    p.bookingId = id;
                    bookingDao.insertSplit(p);
                }
            }
            insertBookingMovement(booking);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Legt eine Umbuchung als zwei verknüpfte Buchungen an (Ausgabe auf {@code from}, Einnahme auf {@code to}). */
    public void saveTransferBooking(final String from, final String to, final long cents,
                                    final String payee, final String note, final long createdAt,
                                    final Runnable onDone) {
        saveTransferBooking(from, to, cents, payee, note, createdAt, "", "", onDone);
    }

    /** Wie {@link #saveTransferBooking}, füllt zusätzlich das Ortsjournal für Von-/Nach-Ort. */
    public void saveTransferBooking(final String from, final String to, final long cents,
                                    final String payee, final String note, final long createdAt,
                                    final String fromPlace, final String toPlace,
                                    final Runnable onDone) {
        executor.execute(() -> {
            String group = UUID.randomUUID().toString();
            insertTransferPair(from, to, cents, payee, note, createdAt, false, group, fromPlace, toPlace);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Aktualisiert eine Umbuchung. Bei App-Umbuchungen (nicht leere {@code transfer_group}) werden beide
     * Seiten neu aufgebaut; bei importierten Einseitern wird die einzelne Buchung angepasst.
     */
    public void updateTransferBooking(final Booking existing, final String from, final String to,
                                      final long cents, final String payee, final String note,
                                      final long createdAt, final Runnable onDone) {
        updateTransferBooking(existing, from, to, cents, payee, note, createdAt, "", "", onDone);
    }

    /** Wie {@link #updateTransferBooking}, aktualisiert zusätzlich das Ortsjournal (Von-/Nach-Ort). */
    public void updateTransferBooking(final Booking existing, final String from, final String to,
                                      final long cents, final String payee, final String note,
                                      final long createdAt, final String fromPlace, final String toPlace,
                                      final Runnable onDone) {
        executor.execute(() -> {
            if (existing.transferGroup != null && !existing.transferGroup.isEmpty()) {
                rollbackTransferPlaces(existing.transferGroup);
                bookingDao.deleteByTransferGroup(existing.transferGroup);
                insertTransferPair(from, to, cents, payee, note, createdAt, existing.exported,
                        existing.transferGroup, fromPlace, toPlace);
            } else {
                // Importierte einseitige Umbuchung: an das ursprüngliche Konto gebunden lassen.
                String orig = existing.account;
                if (to.equalsIgnoreCase(orig)) {
                    existing.isIncome = true;
                    existing.account = orig;
                    existing.transferAccount = from;
                } else {
                    existing.isIncome = false;
                    existing.account = from.equalsIgnoreCase(orig) ? orig : from;
                    existing.transferAccount = to;
                }
                existing.amountCents = cents;
                existing.note = note == null ? "" : note;
                existing.createdAt = createdAt;
                existing.isTransfer = true;
                existing.category = "";
                existing.payee = payee == null ? "" : payee.trim();
                bookingDao.deleteSplits(existing.id);
                if (!existing.payee.isEmpty()) {
                    payeeDao.insertIfAbsent(new Payee(existing.payee));
                }
                accountDao.insertIfAbsent(new Account(existing.account));
                accountDao.insertIfAbsent(new Account(existing.transferAccount));
                bookingDao.update(existing);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Fügt die beiden Seiten einer Umbuchung ein (läuft bereits auf dem Executor-Thread). */
    private void insertTransferPair(String from, String to, long cents, String payee, String note,
                                    long createdAt, boolean exported, String group) {
        insertTransferPair(from, to, cents, payee, note, createdAt, exported, group, "", "");
    }

    /**
     * Fügt die beiden Seiten einer Umbuchung ein und – falls ein echter Ort gewählt ist – die passenden
     * Ort-Bewegungen (Von-Konto: −Betrag am {@code fromPlace}, Nach-Konto: +Betrag am {@code toPlace}).
     * Läuft bereits auf dem Executor-Thread.
     */
    private void insertTransferPair(String from, String to, long cents, String payee, String note,
                                    long createdAt, boolean exported, String group,
                                    String fromPlace, String toPlace) {
        accountDao.insertIfAbsent(new Account(from));
        accountDao.insertIfAbsent(new Account(to));
        String memo = note == null ? "" : note;
        String p = payee == null ? "" : payee.trim();
        if (!p.isEmpty()) {
            payeeDao.insertIfAbsent(new Payee(p));
        }
        boolean fromManaged = isRealPlace(fromPlace);
        boolean toManaged = isRealPlace(toPlace);
        String fromP = fromManaged ? fromPlace.trim() : "";
        String toP = toManaged ? toPlace.trim() : "";
        String moveNote = p.isEmpty() ? "Umbuchung" : "Umbuchung: " + p;

        Booking out = new Booking();
        out.amountCents = cents;
        out.isIncome = false;
        out.account = from;
        out.transferAccount = to;
        out.isTransfer = true;
        out.transferGroup = group;
        out.payee = p;
        out.note = memo;
        out.createdAt = createdAt;
        out.exported = exported;
        out.place = fromP;
        out.placeManaged = fromManaged;
        bookingDao.insert(out);
        if (fromManaged) {
            placeEntryDao.insert(new PlaceEntry(from, fromP, -cents, createdAt, "transfer", moveNote));
        }

        Booking in = new Booking();
        in.amountCents = cents;
        in.isIncome = true;
        in.account = to;
        in.transferAccount = from;
        in.isTransfer = true;
        in.transferGroup = group;
        in.payee = p;
        in.note = memo;
        in.createdAt = createdAt;
        in.exported = exported;
        in.place = toP;
        in.placeManaged = toManaged;
        bookingDao.insert(in);
        if (toManaged) {
            placeEntryDao.insert(new PlaceEntry(to, toP, cents, createdAt, "transfer", moveNote));
        }
    }

    /**
     * Zieht die Ort-Bewegungen einer bestehenden Umbuchung zurück (Gegenbewegung je Seite mit echtem Ort),
     * bevor die Buchungen gelöscht/neu aufgebaut werden. Läuft auf dem Executor-Thread.
     */
    private void rollbackTransferPlaces(String group) {
        if (group == null || group.isEmpty()) {
            return;
        }
        for (Booking b : bookingDao.getByTransferGroup(group)) {
            if (b.placeManaged && isRealPlace(b.place)) {
                placeEntryDao.insert(new PlaceEntry(b.account, b.place, -signed(b),
                        System.currentTimeMillis(), "transfer", "Umbuchung geändert"));
            }
        }
    }

    /** Kategorie-Teile einer Buchung (für den Editor im Bearbeiten-Modus). */
    public void getSplits(final long bookingId, final Callback<List<BookingSplit>> callback) {
        executor.execute(() -> {
            final List<BookingSplit> result = bookingDao.getSplits(bookingId);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Alle Kategorie-Teile, gruppiert nach Buchungs-ID (für Anzeige/Filter in der Liste). */
    public void getAllSplitsMap(final Callback<Map<Long, List<BookingSplit>>> callback) {
        executor.execute(() -> {
            final Map<Long, List<BookingSplit>> map = new HashMap<>();
            for (BookingSplit s : bookingDao.getAllSplits()) {
                List<BookingSplit> list = map.get(s.bookingId);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(s.bookingId, list);
                }
                list.add(s);
            }
            mainHandler.post(() -> callback.onResult(map));
        });
    }

    public void deleteBooking(final long id, final Runnable onDone) {
        executor.execute(() -> {
            Booking old = bookingDao.getById(id);
            bookingDao.deleteSplits(id);
            bookingDao.delete(id);
            // Ort-Journal nachziehen: Gegenbewegung anhängen (alte Bewegung bleibt als Historie stehen).
            if (old != null && old.placeManaged && isRealPlace(old.place)) {
                placeEntryDao.insert(new PlaceEntry(old.account, old.place, -signed(old),
                        System.currentTimeMillis(), "booking", "Buchung gelöscht"));
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Löscht eine Umbuchung: beide Seiten (über {@code group}) oder die einzelne (importierte) Buchung. */
    public void deleteTransfer(final String group, final long fallbackId, final Runnable onDone) {
        executor.execute(() -> {
            if (group != null && !group.isEmpty()) {
                rollbackTransferPlaces(group);
                bookingDao.deleteByTransferGroup(group);
            } else {
                bookingDao.delete(fallbackId);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Beide Seiten einer Umbuchung (für die Ort-Vorbelegung im Editor). */
    public void getTransferGroup(final String group, final Callback<List<Booking>> callback) {
        executor.execute(() -> {
            final List<Booking> result = bookingDao.getByTransferGroup(group);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getBookingById(final long id, final Callback<Booking> callback) {
        executor.execute(() -> {
            final Booking result = bookingDao.getById(id);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Buchungstyp der Wear-Sprach-Erfassung (per Knopf gewählt). */
    public static final String VOICE_TYPE_INCOME = "income";
    public static final String VOICE_TYPE_EXPENSE = "expense";
    public static final String VOICE_TYPE_TRANSFER = "transfer";

    /**
     * Legt aus einem gesprochenen Satz (z. B. „Frisör 20 Euro") synchron eine Buchung an – für die
     * Wear-Anbindung, deren {@code WearableListenerService} bereits auf einem Hintergrund-Thread läuft.
     * Nutzt denselben Parser ({@link de.spahr.ausgaben.voice.VoiceInput}) und dieselbe Vorlagen-/Split-Logik
     * wie die Sprach-Schnellerfassung am Phone. Der {@code type} (Einnahme/Ausgabe/Umbuchung) wird von der
     * Uhr per Knopf vorgegeben und erzwungen. Liefert {@code true}, wenn eine Buchung entstanden ist.
     */
    public boolean createVoiceBookingBlocking(String spokenText, String defaultAccount, String type,
                                              String coords) {
        return createVoiceBookingBlocking(spokenText, defaultAccount, "", type, coords);
    }

    /**
     * @param account gewähltes Konto (Widget/Uhr); bei Umbuchung das Von-Konto.
     * @param place   gewählter Ort des Kontos (leer = Standardort des Kontos).
     */
    public boolean createVoiceBookingBlocking(String spokenText, String account, String place, String type,
                                              String coords) {
        // Bei ausgeschaltetem GPS keinen Standort verwenden: reiner Betrag von der Uhr → leerer Empfänger,
        // keine GPS-Notiz. (Auf der Uhr bleibt die Betrag-only-Erfassung damit möglich.)
        if (!new de.spahr.ausgaben.settings.SettingsStore(appContext).isGpsEnabled()) {
            coords = null;
        }
        de.spahr.ausgaben.voice.VoiceInput.Result parsed =
                de.spahr.ausgaben.voice.VoiceInput.parse(spokenText);
        String term = parsed.payee == null ? "" : parsed.payee.trim();
        long amountCents = parsed.amountCents == null ? 0 : parsed.amountCents;
        if (term.isEmpty() && amountCents <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        String def = account == null ? "" : account.trim();
        String selPlace = place == null ? "" : place.trim();

        // Auflösung: mit Empfänger normal; bei reinem Betrag über den aktuellen Standort (100 m).
        Booking[] resolvedBooking = new Booking[1];
        PayeeCorrection[] resolvedAlias = new PayeeCorrection[1];
        java.util.Set<String> closed = aliasResolver.closedAccounts();
        if (term.isEmpty()) {
            double[] ll = de.spahr.ausgaben.location.Geo.parse(coords);
            if (ll != null) {
                aliasResolver.resolveGps(ll[0], ll[1], closed, resolvedBooking, resolvedAlias);
            }
        } else {
            // Mit Empfänger: bei mehreren gleichnamigen Treffern den zur aktuellen Position nächsten wählen.
            aliasResolver.resolve(term, de.spahr.ausgaben.location.Geo.parse(coords), closed,
                    resolvedBooking, resolvedAlias);
        }
        Booking template = resolvedBooking[0];
        PayeeCorrection alias = resolvedAlias[0];
        if (alias != null) {
            term = alias.corrected;
        }
        long amount = amountCents > 0 ? amountCents : (template != null ? template.amountCents : 0);

        // Umbuchung: von der Uhr erzwungen. Alias liefert Von/Bis, sonst Vorlage (falls Umbuchung),
        // sonst Standardkonto → gesprochener Begriff als Zielkonto.
        if (VOICE_TYPE_TRANSFER.equals(type)) {
            String from;
            String to;
            String payee = "";
            String note = "";
            if (alias != null) {
                from = alias.fromAccount.isEmpty() ? def : alias.fromAccount;
                to = alias.toAccount.isEmpty() ? term : alias.toAccount;
                payee = alias.corrected;
            } else if (template != null && template.isTransfer) {
                from = template.isIncome ? template.transferAccount : template.account;
                to = template.isIncome ? template.account : template.transferAccount;
                payee = template.payee;
                note = template.note;
            } else {
                // Widget/Uhr: gewähltes Konto = Von-Konto; der gesprochene Name (falls vorhanden) ist der
                // Empfänger. Nach-Konto: ist das Von-Konto das Standardkonto → leer (am Handy ergänzen),
                // sonst das Standardkonto.
                from = def;
                payee = term;
                String phoneDefault = new de.spahr.ausgaben.settings.SettingsStore(appContext)
                        .getDefaultAccount().trim();
                to = from.equalsIgnoreCase(phoneDefault) ? "" : phoneDefault;
            }
            note = AliasResolver.appendGps(note, coords);
            de.spahr.ausgaben.settings.PlacesStore ps =
                    new de.spahr.ausgaben.settings.PlacesStore(appContext);
            String fromPlace = alias != null ? alias.fromPlace
                    : (isRealPlace(selPlace) ? selPlace : ps.getDefaultPlace(from));
            String toPlace = alias != null ? alias.toPlace
                    : (to.isEmpty() ? "" : ps.getDefaultPlace(to));
            insertTransferPair(from, to, amount, payee, note, now, false, UUID.randomUUID().toString(),
                    fromPlace, toPlace);
            return true;
        }

        // Einnahme/Ausgabe: Richtung per Knopf erzwungen; Alias bzw. Vorlage liefert Konto/Kategorie.
        boolean income = VOICE_TYPE_INCOME.equals(type);
        boolean useTemplate = alias == null && template != null && !template.isTransfer;
        Booking b = new Booking();
        b.amountCents = amount;
        b.createdAt = now;
        b.exported = false;
        b.isIncome = income;
        if (alias != null) {
            b.payee = alias.corrected;
            b.account = alias.account.isEmpty() ? def : alias.account;
            b.category = income ? AliasResolver.firstNonEmpty(alias.catIncome1, alias.catIncome2)
                    : AliasResolver.firstNonEmpty(alias.catExpense1, alias.catExpense2);
            b.note = "";
        } else if (useTemplate) {
            b.payee = template.payee;
            b.account = template.account;
            b.category = template.category;
            b.note = template.note;
        } else {
            b.payee = term;
            b.account = def;
            b.category = "";
            b.note = "";
        }
        b.note = AliasResolver.appendGps(b.note, coords);
        // Sprach-/Uhr-Buchung ist in der App angelegt → Ort des Alias, sonst gewählter Ort (Widget/Uhr),
        // sonst Standardort des Kontos.
        String aliasPlace = alias != null ? alias.place : "";
        String resolvedPlace;
        if (isRealPlace(aliasPlace)) {
            resolvedPlace = aliasPlace.trim();
        } else if (isRealPlace(selPlace)) {
            resolvedPlace = selPlace;
        } else {
            resolvedPlace = new de.spahr.ausgaben.settings.PlacesStore(appContext).getDefaultPlace(b.account);
        }
        b.place = isRealPlace(resolvedPlace) ? resolvedPlace.trim() : "";
        b.placeManaged = true;
        if (!b.payee.trim().isEmpty()) {
            payeeDao.insertIfAbsent(new Payee(b.payee));
        }
        if (!b.account.trim().isEmpty()) {
            accountDao.insertIfAbsent(new Account(b.account));
        }
        long id = bookingDao.insert(b);
        insertBookingMovement(b);

        // Splitbuchungs-Vorlage: Teilbeträge proportional auf den neuen Betrag skalieren (Rest in letzte Zeile).
        if (useTemplate && template.amountCents != 0) {
            List<BookingSplit> tmplSplits = bookingDao.getSplits(template.id);
            if (tmplSplits != null && tmplSplits.size() >= 2) {
                long assigned = 0;
                for (int i = 0; i < tmplSplits.size(); i++) {
                    BookingSplit s = tmplSplits.get(i);
                    long part;
                    if (i < tmplSplits.size() - 1) {
                        part = Math.round((double) s.amountCents * amount / template.amountCents);
                        assigned += part;
                    } else {
                        part = amount - assigned;
                    }
                    bookingDao.insertSplit(new BookingSplit(id, s.category, part));
                }
            }
        }
        return true;
    }

    // Aliase + Sprach-/Standort-Auflösung → AliasResolver.

    public void saveAlias(PayeeCorrection alias) {
        aliasResolver.saveAlias(alias);
    }

    public void saveAlias(PayeeCorrection alias, boolean mergeGps) {
        aliasResolver.saveAlias(alias, mergeGps);
    }

    public void getAllAliases(Callback<List<PayeeCorrection>> callback) {
        aliasResolver.getAllAliases(callback);
    }

    public void getAlias(long id, Callback<PayeeCorrection> callback) {
        aliasResolver.getAlias(id, callback);
    }

    public void deleteAlias(long id, Runnable onDone) {
        aliasResolver.deleteAlias(id, onDone);
    }

    /** Ergebnis der Sprach-Empfängersuche: Vorlage-Buchung und/oder Alias + aufzulösender Empfänger. */
    public static final class VoiceResolution {
        /** Passende Vorlage-Buchung oder {@code null}. */
        public final Booking booking;
        /** Passender Alias oder {@code null}. */
        public final PayeeCorrection alias;
        /** Aufzulösender Empfänger – bereits korrigiert, falls ein Alias greift. */
        public final String payee;

        public VoiceResolution(Booking booking, PayeeCorrection alias, String payee) {
            this.booking = booking;
            this.alias = alias;
            this.payee = payee;
        }
    }

    public void resolveVoice(String term, String coords, Callback<VoiceResolution> callback) {
        aliasResolver.resolveVoice(term, coords, callback);
    }

    public void resolveVoiceByGps(String coords, Callback<VoiceResolution> callback) {
        aliasResolver.resolveVoiceByGps(coords, callback);
    }

    /**
     * Fügt importierte Buchungen ein (jeweils mit gesetztem exported-Flag) und ergänzt
     * Konto/Empfänger als Auswahlwerte. Liefert die Anzahl eingefügter Buchungen.
     */
    public void importBookings(final List<Booking> bookings, final Callback<Integer> callback) {
        executor.execute(() -> {
            for (Booking b : bookings) {
                insertImported(b);
            }
            final int count = bookings.size();
            mainHandler.post(() -> callback.onResult(count));
        });
    }

    /**
     * Ersetzt den Import eines Kontos: löscht zuerst alle bereits exportierten Buchungen dieses Kontos
     * und fügt anschließend die importierten Buchungen ein. Liefert die Anzahl eingefügter Buchungen.
     */
    public void replaceImport(final String account, final List<Booking> bookings,
                              final Callback<Integer> callback) {
        executor.execute(() -> {
            if (account != null && !account.trim().isEmpty()) {
                bookingDao.deleteSplitsForExportedAccount(account.trim());
                bookingDao.deleteExportedByAccount(account.trim());
            }
            for (Booking b : bookings) {
                insertImported(b);
            }
            final int count = bookings.size();
            mainHandler.post(() -> callback.onResult(count));
        });
    }

    /** Fügt eine importierte Buchung samt ihren Kategorie-Teilen ein (läuft auf dem Executor-Thread). */
    private void insertImported(Booking b) {
        if (!b.payee.trim().isEmpty()) {
            payeeDao.insertIfAbsent(new Payee(b.payee));
        }
        accountDao.insertIfAbsent(new Account(b.account));
        long id = bookingDao.insert(b);
        if (b.parts != null) {
            for (BookingSplit p : b.parts) {
                p.bookingId = id;
                bookingDao.insertSplit(p);
            }
        }
    }

    /**
     * Ersetzt den Import mehrerer Konten in einem Durchgang: je Konto zuerst die bereits exportierten
     * Buchungen löschen, dann die importierten einfügen. Liefert {@code [Konten, Buchungen]}.
     */
    public void replaceImportAccounts(final java.util.LinkedHashMap<String, List<Booking>> byAccount,
                                      final Callback<int[]> callback) {
        executor.execute(() -> {
            int accounts = 0;
            int inserted = 0;
            for (java.util.Map.Entry<String, List<Booking>> e : byAccount.entrySet()) {
                String account = e.getKey();
                if (account != null && !account.trim().isEmpty()) {
                    bookingDao.deleteSplitsForExportedAccount(account.trim());
                    bookingDao.deleteExportedByAccount(account.trim());
                    accountDao.insertIfAbsent(new Account(account.trim()));
                }
                for (Booking b : e.getValue()) {
                    insertImported(b);
                    inserted++;
                }
                accounts++;
            }
            final int fa = accounts;
            final int fi = inserted;
            mainHandler.post(() -> callback.onResult(new int[]{fa, fi}));
        });
    }

    /** Löscht ein komplettes Konto: alle Buchungen dieses Kontos und den Konto-Eintrag selbst. */
    public void deleteAccount(final String account, final Runnable onDone) {
        executor.execute(() -> {
            if (account != null && !account.trim().isEmpty()) {
                bookingDao.deleteSplitsForAccount(account.trim());
                bookingDao.deleteAllByAccount(account.trim());
                placeEntryDao.deleteByAccount(account.trim());
                accountDao.deleteByName(account.trim());
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Löscht alle Buchungen sowie die Konto-/Empfänger-Vorschlagslisten (Einstellungen bleiben). */
    public void resetBookingData(final Runnable onDone) {
        executor.execute(() -> {
            bookingDao.deleteAllSplits();
            bookingDao.deleteAll();
            accountDao.deleteAll();
            payeeDao.deleteAll();
            correctionDao.deleteAll();
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    public void getAllBookings(final Callback<List<Booking>> callback) {
        executor.execute(() -> {
            final List<Booking> result = bookingDao.getAllBookings();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getPayeeNames(final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = payeeDao.getAllNames();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Nur aktive Konten – geschlossene Konten sind nirgends auswählbar. */
    public void getAccountNames(final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = accountDao.getActiveNames();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Alle Konten mit Status (aktiv/geschlossen) – für die Konto-Verwaltung. */
    public void getAllAccountsWithStatus(final Callback<List<Account>> callback) {
        executor.execute(() -> {
            final List<Account> result = accountDao.getAllOrdered();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Konto schließen (inaktiv) oder wieder öffnen. */
    public void setAccountClosed(final String name, final boolean closed, final Runnable onDone) {
        executor.execute(() -> {
            if (name != null && !name.trim().isEmpty()) {
                accountDao.setClosed(name.trim(), closed);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Saldo (Einnahmen − Ausgaben) je Konto; fehlende Konten = 0. Für die Mehrfach-Konto-Verwaltung. */
    public void getAllAccountBalances(final Callback<Map<String, Long>> callback) {
        executor.execute(() -> {
            final Map<String, Long> result = new HashMap<>();
            for (AccountBalance ab : bookingDao.getAllAccountBalances()) {
                if (ab.name != null) {
                    result.put(ab.name, ab.balance);
                }
            }
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Schließt/öffnet mehrere Konten in einem Rutsch; {@code onDone} läuft einmal am Ende (Main-Thread). */
    public void setAccountsClosed(final List<String> names, final boolean closed, final Runnable onDone) {
        executor.execute(() -> {
            if (names != null) {
                for (String name : names) {
                    if (name != null && !name.trim().isEmpty()) {
                        accountDao.setClosed(name.trim(), closed);
                    }
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Löscht mehrere Konten samt Buchungen in einem Rutsch; {@code onDone} läuft einmal am Ende. */
    public void deleteAccounts(final List<String> accounts, final Runnable onDone) {
        executor.execute(() -> {
            if (accounts != null) {
                for (String account : accounts) {
                    if (account != null && !account.trim().isEmpty()) {
                        bookingDao.deleteSplitsForAccount(account.trim());
                        bookingDao.deleteAllByAccount(account.trim());
                        placeEntryDao.deleteByAccount(account.trim());
                        accountDao.deleteByName(account.trim());
                    }
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    public void getCategoryNames(final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = bookingDao.getDistinctCategories();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Kategorien getrennt nach Ausgabe/Einnahme (eine Kategorie darf in beiden vorkommen). */
    public void getCategoriesGrouped(final Callback<CategoryGroups> callback) {
        executor.execute(() -> {
            final CategoryGroups g = new CategoryGroups(
                    bookingDao.getExpenseCategories(), bookingDao.getIncomeCategories());
            mainHandler.post(() -> callback.onResult(g));
        });
    }

    /** Kategorien nach Buchungsart getrennt. */
    public static final class CategoryGroups {
        public final List<String> expense;
        public final List<String> income;
        public CategoryGroups(List<String> expense, List<String> income) {
            this.expense = expense;
            this.income = income;
        }
    }

    // ---- Budgetplanung ----

    /** Gespeichertes Budget eines Jahres (Soll-Werte je Kategorie + Herkunft). */
    public static final class YearBudget {
        /** {@code "kmy"}/{@code "internal"}; {@code null} = keins vorhanden. */
        public final String source;
        public final List<Budget> lines;
        public YearBudget(String source, List<Budget> lines) {
            this.source = source;
            this.lines = lines;
        }
    }

    // Budgetplanung → BudgetRepository.

    public void getCategoryActuals(long fromMs, long toMs, Callback<List<CategorySum>> callback) {
        budgetRepo.getCategoryActuals(fromMs, toMs, callback);
    }

    /** Kategorie-Pfad → Typ ({@code true} = Einnahme) aus der Datei; verlässliche Budget-Einordnung. */
    public void getCategoryTypes(Callback<Map<String, Boolean>> callback) {
        budgetRepo.getCategoryTypes(callback);
    }

    /** Historische Zahlungs-Verteilung je Kategorie (für die verlaufsbasierte Budget-Balkenfarbe). */
    public void getCategoryTiming(boolean monthView, Callback<List<CategoryBucket>> callback) {
        budgetRepo.getCategoryTiming(monthView, callback);
    }

    public void getBudget(int year, Callback<YearBudget> callback) {
        budgetRepo.getBudget(year, callback);
    }

    public void saveBudgetLine(int year, String category, boolean isIncome, long amountCents,
                               Runnable onDone) {
        budgetRepo.saveBudgetLine(year, category, isIncome, amountCents, onDone);
    }

    public void replaceBudget(int year, String source, List<Budget> lines, Runnable onDone) {
        budgetRepo.replaceBudget(year, source, lines, onDone);
    }

    public void computeBudgetFromHistory(int year, Runnable onDone) {
        budgetRepo.computeBudgetFromHistory(year, onDone);
    }

    // ---- Depot (Wertpapiere) ----

    /** Aktueller Bestand eines Wertpapiers: Name/Symbol, Stückzahl, Kurs, Wert (Cent). */
    public static final class DepotHolding {
        public final String name;
        public final String symbol;
        public final String kmyId;
        public final double shares;
        public final double price;
        public final long valueCents;

        DepotHolding(String name, String symbol, String kmyId, double shares, double price,
                     long valueCents) {
            this.name = name;
            this.symbol = symbol;
            this.kmyId = kmyId;
            this.shares = shares;
            this.price = price;
            this.valueCents = valueCents;
        }
    }

    /** Kennzahlen: Depotwert, Käufe/Verkäufe/Dividenden, Nettoeinsatz (Käufe − Verkäufe − Dividenden),
     *  Gewinn/Verlust (Cent + Prozent). */
    public static final class DepotMetrics {
        public final long valueCents;
        public final long buyCents;
        public final long sellCents;
        public final long dividendCents;
        public final long netInvestedCents;
        public final long gainCents;
        public final double gainPct;
        DepotMetrics(long value, long buy, long sell, long dividend) {
            this.valueCents = value;
            this.buyCents = buy;
            this.sellCents = sell;
            this.dividendCents = dividend;
            this.netInvestedCents = buy - sell - dividend;
            this.gainCents = value - netInvestedCents;
            this.gainPct = netInvestedCents != 0
                    ? (double) gainCents / Math.abs(netInvestedCents) * 100.0 : 0.0;
        }
    }

    // Depot → DepotRepository.

    public void replaceDepotImport(String depot, List<Security> securities,
                                   List<SecurityTx> transactions, Runnable onDone) {
        depotRepo.replaceDepotImport(depot, securities, transactions, onDone);
    }

    public void getDepots(Callback<List<String>> callback) {
        depotRepo.getDepots(callback);
    }

    public void getDepotHoldings(String depot, Callback<List<DepotHolding>> callback) {
        depotRepo.getDepotHoldings(depot, callback);
    }

    public void getSecurityTransactions(String depot, String kmyId,
                                        Callback<List<SecurityTx>> callback) {
        depotRepo.getSecurityTransactions(depot, kmyId, callback);
    }

    public void getDepotMetrics(String depot, Callback<DepotMetrics> callback) {
        depotRepo.getDepotMetrics(depot, callback);
    }

    public void getSecurityMetrics(String depot, String kmyId, Callback<DepotMetrics> callback) {
        depotRepo.getSecurityMetrics(depot, kmyId, callback);
    }

    /** Stellt sicher, dass ein Kontoname als Auswahlwert existiert (z. B. Standardkonto). */
    public void ensureAccount(final String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        executor.execute(() -> accountDao.insertIfAbsent(new Account(name.trim())));
    }

    // ---- Bargeld-Orte ----

    private static final String NO_PLACE = de.spahr.ausgaben.settings.PlacesStore.NO_PLACE;

    private boolean isRealPlace(String place) {
        return place != null && !place.trim().isEmpty() && !place.equals(NO_PLACE);
    }

    /**
     * Speichert eine neue (in der App angelegte) Buchung und legt – falls ein echter Ort gewählt ist
     * (Standardort zählt dazu) – eine passende Ort-Bewegung im Journal an. {@code place} leer/„ohne Ort"
     * → keine Bewegung, die Buchung fällt in den Rest „ohne Ort". Die Buchung merkt sich ihren Ort-Link
     * ({@code place_managed}), damit spätere Änderungen als Ausgleichs-Bewegung nachgezogen werden.
     */
    public void saveBookingWithPlace(final Booking booking, final String place, final Runnable onDone) {
        booking.place = isRealPlace(place) ? place.trim() : "";
        booking.placeManaged = true;
        executor.execute(() -> {
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            bookingDao.insert(booking);
            insertBookingMovement(booking);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Vorzeichenbehafteter Betrag einer Buchung (Einnahme = +, Ausgabe = −). */
    private static long signed(Booking b) {
        return b.isIncome ? b.amountCents : -b.amountCents;
    }

    /**
     * Legt für eine neu angelegte, ort-verknüpfte Buchung die anfängliche Ort-Bewegung an (nur wenn ein
     * echter Ort hinterlegt ist). Datum = Buchungsdatum. Läuft auf dem Executor-Thread.
     */
    private void insertBookingMovement(Booking b) {
        if (b.placeManaged && isRealPlace(b.place)) {
            String note = b.payee == null || b.payee.trim().isEmpty()
                    ? "Buchung" : "Buchung: " + b.payee.trim();
            placeEntryDao.insert(new PlaceEntry(b.account, b.place, signed(b), b.createdAt, "booking", note));
        }
    }

    /** Ordnet noch nicht zugeordnete Ort-Bewegungen einmalig dem Standardkonto zu (Migration v4→v5). */
    public void migratePlaceEntryAccounts(final String defaultAccount) {
        if (defaultAccount == null || defaultAccount.trim().isEmpty()) {
            return;
        }
        executor.execute(() -> placeEntryDao.assignEmptyAccount(defaultAccount.trim()));
    }

    public void getTotalBalance(final Callback<Long> callback) {
        executor.execute(() -> {
            final long result = bookingDao.getTotalBalance();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Saldo eines einzelnen Kontos (für „Orte nur fürs Standardkonto"). */
    public void getAccountBalance(final String account, final Callback<Long> callback) {
        executor.execute(() -> {
            final long result = bookingDao.getBalanceByAccount(account == null ? "" : account);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Kontosaldo bis einschließlich der angegebenen Buchung (für die „vorher/nachher"-Anzeige). */
    public void getAccountBalanceUpTo(final String account, final long createdAt, final long id,
                                      final Callback<Long> callback) {
        executor.execute(() -> {
            final long result = bookingDao.getBalanceUpTo(account == null ? "" : account, createdAt, id);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Ort-Salden eines Kontos aus dem Journal (Σ Bewegungen je Ort). „ohne Ort" ist der berechnete Rest. */
    public void getPlaceBalances(final String account, final Callback<List<PlaceBalance>> callback) {
        executor.execute(() -> {
            final List<PlaceBalance> result = placeEntryDao.getBalances(account == null ? "" : account);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Ort-Salden je (Konto, Ort) über alle Konten aus dem Journal (für die Bestände-Gruppenliste). */
    public void getAllPlaceBalances(final Callback<List<PlaceBalance>> callback) {
        executor.execute(() -> {
            final List<PlaceBalance> result = placeEntryDao.getAllBalances();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getPlaceHistory(final String account, final String place,
                                final Callback<List<PlaceEntry>> callback) {
        executor.execute(() -> {
            final List<PlaceEntry> result = placeEntryDao.getByPlace(
                    account == null ? "" : account, place);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getAllPlaceEntries(final Callback<List<PlaceEntry>> callback) {
        executor.execute(() -> {
            final List<PlaceEntry> result = placeEntryDao.getAll();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Umbuchen zwischen Orten desselben Kontos (keine Buchung). */
    public void saveTransfer(final String account, final String from, final String to,
                             final long cents, final Runnable onDone) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            if (isRealPlace(from)) {
                placeEntryDao.insert(new PlaceEntry(account, from.trim(), -cents, now, "transfer"));
            }
            if (isRealPlace(to)) {
                placeEntryDao.insert(new PlaceEntry(account, to.trim(), cents, now, "transfer"));
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Kassensturz: setzt den Saldo eines Ortes (im Konto {@code account}) auf {@code targetCents} und
     * bucht die Differenz optional als Buchung auf dieses Konto (Empfänger „Unbekannt", Kategorie
     * „Sonstiges").
     */
    public void saveReconcile(final String account, final String place, final long targetCents,
                              final boolean createBooking, final Runnable onDone) {
        executor.execute(() -> {
            String acct = account == null ? "" : account;
            // Ist-Saldo aus dem Ort-Journal (Summe der Bewegungen dieses Orts).
            long current = placeEntryDao.getBalance(acct, place);
            long diff = targetCents - current;
            if (diff != 0) {
                long now = System.currentTimeMillis();
                placeEntryDao.insert(new PlaceEntry(account, place, diff, now, "reconcile"));
                if (createBooking) {
                    Booking b = new Booking();
                    b.amountCents = Math.abs(diff);
                    b.isIncome = diff >= 0;
                    b.payee = "Unbekannt";
                    b.account = account == null ? "" : account;
                    b.category = "Sonstiges";
                    b.note = "Kassensturz " + place;
                    b.createdAt = now;
                    b.exported = false;
                    if (!b.account.isEmpty()) {
                        accountDao.insertIfAbsent(new Account(b.account));
                    }
                    payeeDao.insertIfAbsent(new Payee(b.payee));
                    bookingDao.insert(b);
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /**
     * Aktualisiert eine in der App angelegte, ort-verknüpfte Buchung und zieht die Ort-Salden per
     * angehängter Ausgleichs-Bewegung(en) nach – die alten Bewegungen bleiben als Historie stehen (Datum
     * der Änderung = jetzt). {@code newPlace} leer/„ohne Ort" → keine Bewegung (fällt in den Rest).
     * {@code parts} ersetzt die Kategorie-Teile (leer/null → Einzelkategorie). Erzeugt/ändert keine
     * andere Buchung.
     */
    public void updateBookingWithPlace(final Booking booking, final String newPlace,
                                       final List<BookingSplit> parts, final Runnable onDone) {
        executor.execute(() -> {
            Booking old = bookingDao.getById(booking.id);
            String np = isRealPlace(newPlace) ? newPlace.trim() : "";
            long now = System.currentTimeMillis();
            boolean oldReal = old != null && old.placeManaged && isRealPlace(old.place);
            long oldSigned = old != null ? signed(old) : 0;
            String oldPlace = old != null ? old.place : "";
            String oldAccount = old != null ? old.account : booking.account;
            boolean newReal = isRealPlace(np);
            long newSigned = signed(booking);
            String bookingNote = booking.payee == null || booking.payee.trim().isEmpty()
                    ? "Buchung geändert" : "Buchung: " + booking.payee.trim();
            if (oldReal && newReal && oldPlace.equals(np) && oldAccount.equals(booking.account)) {
                long delta = newSigned - oldSigned;
                if (delta != 0) {
                    placeEntryDao.insert(new PlaceEntry(booking.account, np, delta, now, "booking",
                            "Buchung geändert"));
                }
            } else {
                if (oldReal) {
                    placeEntryDao.insert(new PlaceEntry(oldAccount, oldPlace, -oldSigned, now, "booking",
                            "Buchung umgebucht/geändert"));
                }
                if (newReal) {
                    placeEntryDao.insert(new PlaceEntry(booking.account, np, newSigned, now, "booking",
                            bookingNote));
                }
            }
            booking.place = np;
            booking.placeManaged = true;
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            bookingDao.update(booking);
            bookingDao.deleteSplits(booking.id);
            if (parts != null) {
                for (BookingSplit p : parts) {
                    p.bookingId = booking.id;
                    bookingDao.insertSplit(p);
                }
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    // ---- Ort-Bewegungen einzeln bearbeiten (nur Ortssaldo, keine Buchung) ----

    /** Fügt eine manuelle Ort-Bewegung ins Journal ein. */
    public void addPlaceMovement(final String account, final String place, final long cents,
                                 final long dateMillis, final String note, final Runnable onDone) {
        executor.execute(() -> {
            placeEntryDao.insert(new PlaceEntry(account == null ? "" : account,
                    place == null ? "" : place, cents, dateMillis, "transfer",
                    note == null ? "" : note));
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Aktualisiert eine einzelne Ort-Bewegung (Datum/Betrag/Notiz). */
    public void updatePlaceMovement(final PlaceEntry entry, final Runnable onDone) {
        executor.execute(() -> {
            placeEntryDao.update(entry);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Löscht eine einzelne Ort-Bewegung. */
    public void deletePlaceMovement(final long id, final Runnable onDone) {
        executor.execute(() -> {
            placeEntryDao.delete(id);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    public void renamePlaceEntries(final String account, final String oldName, final String newName,
                                   final Runnable onDone) {
        executor.execute(() -> {
            String acct = account == null ? "" : account;
            placeEntryDao.renamePlace(acct, oldName, newName);
            bookingDao.renamePlace(acct, oldName, newName); // Buchungen folgen dem Umbenennen
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    public void deletePlaceEntries(final String account, final String place, final Runnable onDone) {
        executor.execute(() -> {
            placeEntryDao.deleteByPlace(account == null ? "" : account, place);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Liefert die Direktreferenz auf den BookingDao – nur für Hintergrund-Aufgaben verwenden. */
    public BookingDao bookingDao() {
        return bookingDao;
    }

    public ExecutorService executor() {
        return executor;
    }

    public Handler mainHandler() {
        return mainHandler;
    }
}
