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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public Repository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.bookingDao = db.bookingDao();
        this.accountDao = db.accountDao();
        this.payeeDao = db.payeeDao();
        this.placeEntryDao = db.placeEntryDao();
        this.correctionDao = db.payeeCorrectionDao();
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
            if (isRealPlace(place)) {
                long signed = booking.isIncome ? booking.amountCents : -booking.amountCents;
                placeEntryDao.insert(new PlaceEntry(booking.account, place.trim(), signed,
                        booking.createdAt, "booking"));
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Legt eine Umbuchung als zwei verknüpfte Buchungen an (Ausgabe auf {@code from}, Einnahme auf {@code to}). */
    public void saveTransferBooking(final String from, final String to, final long cents,
                                    final String payee, final String note, final long createdAt,
                                    final Runnable onDone) {
        executor.execute(() -> {
            String group = UUID.randomUUID().toString();
            insertTransferPair(from, to, cents, payee, note, createdAt, false, group);
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
        executor.execute(() -> {
            if (existing.transferGroup != null && !existing.transferGroup.isEmpty()) {
                bookingDao.deleteByTransferGroup(existing.transferGroup);
                insertTransferPair(from, to, cents, payee, note, createdAt, existing.exported,
                        existing.transferGroup);
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
        accountDao.insertIfAbsent(new Account(from));
        accountDao.insertIfAbsent(new Account(to));
        String memo = note == null ? "" : note;
        String p = payee == null ? "" : payee.trim();
        if (!p.isEmpty()) {
            payeeDao.insertIfAbsent(new Payee(p));
        }
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
        bookingDao.insert(out);
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
        bookingDao.insert(in);
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
            bookingDao.deleteSplits(id);
            bookingDao.delete(id);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    /** Löscht eine Umbuchung: beide Seiten (über {@code group}) oder die einzelne (importierte) Buchung. */
    public void deleteTransfer(final String group, final long fallbackId, final Runnable onDone) {
        executor.execute(() -> {
            if (group != null && !group.isEmpty()) {
                bookingDao.deleteByTransferGroup(group);
            } else {
                bookingDao.delete(fallbackId);
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
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
    public boolean createVoiceBookingBlocking(String spokenText, String defaultAccount, String type) {
        de.spahr.ausgaben.voice.VoiceInput.Result parsed =
                de.spahr.ausgaben.voice.VoiceInput.parse(spokenText);
        String term = parsed.payee == null ? "" : parsed.payee.trim();
        long amountCents = parsed.amountCents == null ? 0 : parsed.amountCents;
        if (term.isEmpty() && amountCents <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        String def = defaultAccount == null ? "" : defaultAccount.trim();

        // Gelernte Namenskorrektur anwenden, falls der Begriff in den Buchungen unbekannt ist.
        term = applyCorrection(term);
        Booking template = findVoiceTemplate(term);
        long amount = amountCents > 0 ? amountCents : (template != null ? template.amountCents : 0);

        // Umbuchung: von der Uhr erzwungen. Vorlage (falls Umbuchung) liefert Von/Nach + Empfänger,
        // sonst Standardkonto → gesprochener Begriff als Zielkonto.
        if (VOICE_TYPE_TRANSFER.equals(type)) {
            String from;
            String to;
            String payee = "";
            String note = "";
            if (template != null && template.isTransfer) {
                from = template.isIncome ? template.transferAccount : template.account;
                to = template.isIncome ? template.account : template.transferAccount;
                payee = template.payee;
                note = template.note;
            } else {
                from = def;
                to = term;
            }
            insertTransferPair(from, to, amount, payee, note, now, false, UUID.randomUUID().toString());
            return true;
        }

        // Einnahme/Ausgabe: Richtung per Knopf erzwungen; Vorlage (falls keine Umbuchung) liefert
        // Konto/Kategorie/Notiz.
        boolean useTemplate = template != null && !template.isTransfer;
        Booking b = new Booking();
        b.amountCents = amount;
        b.createdAt = now;
        b.exported = false;
        b.isIncome = VOICE_TYPE_INCOME.equals(type);
        if (useTemplate) {
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
        if (!b.payee.trim().isEmpty()) {
            payeeDao.insertIfAbsent(new Payee(b.payee));
        }
        if (!b.account.trim().isEmpty()) {
            accountDao.insertIfAbsent(new Account(b.account));
        }
        long id = bookingDao.insert(b);

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

    /**
     * Wendet – falls nötig – eine gelernte Namenskorrektur an: Ist der Begriff in den Buchungen bekannt
     * (gleiche Suchlogik wie {@link #findVoiceTemplate}), bleibt er unverändert. Sonst wird die Korrektur-
     * Datenbank mit derselben Logik durchsucht (exakter Teilstring, sonst unscharf) und der gelernte
     * richtige Empfänger zurückgegeben. Wird nichts gefunden, bleibt der ursprüngliche Begriff erhalten.
     */
    private String applyCorrection(String term) {
        String t = term == null ? "" : term.trim();
        if (t.isEmpty() || findVoiceTemplate(t) != null) {
            return t; // leer oder in Buchungen bekannt → keine Korrektur nötig
        }
        String corrected = correctionDao.findCorrectedBySpokenLike(t);
        if (corrected == null || corrected.trim().isEmpty()) {
            String bestSpoken = de.spahr.ausgaben.voice.VoiceInput.bestFuzzyPayee(
                    t, correctionDao.getAllSpoken());
            if (bestSpoken != null) {
                corrected = correctionDao.findCorrectedBySpokenExact(bestSpoken);
            }
        }
        return corrected != null && !corrected.trim().isEmpty() ? corrected.trim() : t;
    }

    /** Merkt sich eine Namenskorrektur (falsch erkannt → richtig) für die Spracherkennung. */
    public void saveCorrection(final String spoken, final String corrected) {
        executor.execute(() -> {
            String s = spoken == null ? "" : spoken.trim().toLowerCase(Locale.GERMANY);
            String c = corrected == null ? "" : corrected.trim();
            if (!s.isEmpty() && !c.isEmpty()) {
                correctionDao.upsert(new PayeeCorrection(s, c, System.currentTimeMillis()));
            }
        });
    }

    /** Vorlage-Buchung per Empfänger suchen (exakter Teilstring, sonst unscharf). */
    private Booking findVoiceTemplate(String term) {
        if (term == null || term.isEmpty()) {
            return null;
        }
        Booking template = bookingDao.findLatestByPayeeLike(term);
        if (template == null) {
            String best = de.spahr.ausgaben.voice.VoiceInput.bestFuzzyPayee(
                    term, bookingDao.getDistinctPayees());
            if (best != null) {
                template = bookingDao.findLatestByPayeeLike(best);
            }
        }
        return template;
    }

    /** Ergebnis der Sprach-Empfängersuche: Vorlage-Buchung (falls vorhanden) + aufzulösender Empfänger. */
    public static final class VoiceResolution {
        /** Passende Vorlage-Buchung oder {@code null}, wenn keine gefunden wurde. */
        public final Booking booking;
        /** Aufzulösender Empfänger – bereits korrigiert, falls eine Korrektur gelernt wurde. */
        public final String payee;

        public VoiceResolution(Booking booking, String payee) {
            this.booking = booking;
            this.payee = payee;
        }
    }

    /**
     * Löst einen gesprochenen Empfänger für die Sprach-Erfassung auf: zuerst über die Buchungen (exakter
     * Teilstring, sonst unscharf, z. B. „Friseur" → „Frisör Frank"). Wird dort nichts gefunden, greift
     * eine gelernte Namenskorrektur (gleiche Suchlogik über die Korrektur-Datenbank). Liefert die
     * gefundene Vorlage-Buchung und den – ggf. korrigierten – Empfänger für die Vorbelegung.
     */
    public void resolveVoice(final String term, final Callback<VoiceResolution> callback) {
        executor.execute(() -> {
            String t = term == null ? "" : term.trim();
            String effective = applyCorrection(t);
            Booking result = findVoiceTemplate(effective);
            final VoiceResolution res = new VoiceResolution(result, effective.isEmpty() ? t : effective);
            mainHandler.post(() -> callback.onResult(res));
        });
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

    public void getAccountNames(final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = accountDao.getAllNames();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getCategoryNames(final Callback<List<String>> callback) {
        executor.execute(() -> {
            final List<String> result = bookingDao.getDistinctCategories();
            mainHandler.post(() -> callback.onResult(result));
        });
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
     * Speichert eine neue Buchung und verschiebt zusätzlich den Saldo des gewählten Ortes (kontobezogen;
     * Ort = Konto der Buchung). Ort wird NICHT an der Buchung gespeichert; „ohne Ort"/Standardort → keine
     * explizite Ort-Bewegung (fließt automatisch in den Standardort-Rest).
     */
    public void saveBookingWithPlace(final Booking booking, final String place, final Runnable onDone) {
        executor.execute(() -> {
            payeeDao.insertIfAbsent(new Payee(booking.payee));
            accountDao.insertIfAbsent(new Account(booking.account));
            bookingDao.insert(booking);
            if (isRealPlace(place)) {
                long signed = booking.isIncome ? booking.amountCents : -booking.amountCents;
                placeEntryDao.insert(new PlaceEntry(booking.account, place.trim(), signed,
                        booking.createdAt, "booking"));
            }
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
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

    /** Ort-Salden eines Kontos. */
    public void getPlaceBalances(final String account, final Callback<List<PlaceBalance>> callback) {
        executor.execute(() -> {
            final List<PlaceBalance> result = placeEntryDao.getBalances(account == null ? "" : account);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Ort-Salden je (Konto, Ort) über alle Konten (für die Bestände-Gruppenliste). */
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
            long current = placeEntryDao.getBalance(account == null ? "" : account, place);
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

    public void renamePlaceEntries(final String account, final String oldName, final String newName,
                                   final Runnable onDone) {
        executor.execute(() -> {
            placeEntryDao.renamePlace(account == null ? "" : account, oldName, newName);
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
