package de.spahr.ausgaben.db;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import de.spahr.ausgaben.db.Repository.Callback;
import de.spahr.ausgaben.db.Repository.VoiceResolution;

/**
 * Alias-Verwaltung und Sprach-/Standort-Auflösung: gelernte Empfänger-Aliase (CRUD) sowie das Auffinden einer
 * Vorlage-Buchung bzw. eines Alias zu einem gesprochenen Begriff oder einer Position. Reine Lese-Auflösung –
 * das eigentliche Anlegen der Uhr-/Sprachbuchung bleibt in {@link Repository}. Kollaborator hinter der Fassade.
 */
class AliasResolver {

    private final BookingDao bookingDao;
    private final PayeeCorrectionDao correctionDao;
    private final AccountDao accountDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    AliasResolver(BookingDao bookingDao, PayeeCorrectionDao correctionDao, AccountDao accountDao,
                  ExecutorService executor, Handler mainHandler) {
        this.bookingDao = bookingDao;
        this.correctionDao = correctionDao;
        this.accountDao = accountDao;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /**
     * Sucht einen gelernten Alias zum Begriff – gleiche Logik wie die Buchungssuche: exakter Teilstring
     * ({@code spoken}), sonst unscharf über alle bekannten {@code spoken}. Liefert den vollen Alias oder
     * {@code null}.
     */
    private PayeeCorrection matchAlias(String term, boolean preferred, double[] coords,
                                       java.util.Set<String> closed) {
        String t = term == null ? "" : term.trim();
        if (t.isEmpty()) {
            return null;
        }
        int pref = preferred ? 1 : 0;
        List<PayeeCorrection> matches = correctionDao.findAllBySpokenLike(t, pref);
        if (matches.isEmpty()) {
            String bestSpoken = de.spahr.ausgaben.voice.VoiceInput.bestFuzzyPayee(
                    t, correctionDao.getSpokenByPreferred(pref));
            if (bestSpoken != null) {
                matches = correctionDao.findAllBySpokenExact(bestSpoken, pref);
            }
        }
        // Nur offene (nicht auf geschlossene Konten zeigende) Aliase.
        List<PayeeCorrection> open = new ArrayList<>();
        for (PayeeCorrection a : matches) {
            if (!aliasBlocked(a, closed)) {
                open.add(a);
            }
        }
        if (open.isEmpty()) {
            return null;
        }
        // Mehrere gleichnamige Aliase + bekannter Standort → den nächstgelegenen wählen. Der Nutzer hat den
        // Empfänger explizit genannt, daher ohne Radius-Deckel. Sonst der neueste (open ist created_at DESC).
        if (open.size() > 1 && coords != null) {
            PayeeCorrection near = nearestAliasUncapped(coords[0], coords[1], open);
            if (near != null) {
                return near;
            }
        }
        return open.get(0);
    }

    /** Kleinste Entfernung des aktuellen Standorts zu einer der hinterlegten Alias-Koordinaten. */
    private double nearestPointMeters(double lat, double lon, PayeeCorrection a) {
        double best = Double.MAX_VALUE;
        for (double[] p : a.gpsPoints()) {
            double d = de.spahr.ausgaben.location.Geo.distanceMeters(lat, lon, p[0], p[1]);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    /** Nächstgelegener Alias mit Standort, ohne Radius-Deckel (für explizit genannte Empfänger). */
    private PayeeCorrection nearestAliasUncapped(double lat, double lon, List<PayeeCorrection> list) {
        PayeeCorrection best = null;
        double bestD = Double.MAX_VALUE;
        for (PayeeCorrection a : list) {
            double d = nearestPointMeters(lat, lon, a);
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best;
    }

    /**
     * Auflösung in der gewünschten Reihenfolge: zuerst die <b>bevorzugten</b> Aliase, dann die bestehenden
     * Buchungen, erst danach die übrigen Aliase. Setzt {@code out[0]}=Vorlage-Buchung, {@code out[1]}=Alias.
     * Geschlossene Konten ({@code closed}) werden übersprungen.
     */
    void resolve(String term, double[] coords, java.util.Set<String> closed,
                 Booking[] outBooking, PayeeCorrection[] outAlias) {
        PayeeCorrection alias = matchAlias(term, true, coords, closed);
        Booking booking = null;
        if (alias == null) {
            booking = findVoiceTemplate(term, coords, closed);
            if (booking == null) {
                alias = matchAlias(term, false, coords, closed);
            }
        }
        outBooking[0] = booking;
        outAlias[0] = alias;
    }

    /**
     * Auflösung per Standort (Betrag-only): innerhalb {@link de.spahr.ausgaben.location.Geo#RADIUS_M} in
     * strenger Reihenfolge – bevorzugte Aliase → Buchungen → übrige Aliase; der erste Tier mit Treffer
     * gewinnt, innerhalb eines Tiers der nächstgelegene. Geschlossene Konten werden übersprungen.
     */
    void resolveGps(double lat, double lon, java.util.Set<String> closed,
                    Booking[] outBooking, PayeeCorrection[] outAlias) {
        PayeeCorrection a = nearestAlias(lat, lon, openAliases(correctionDao.getWithGps(1), closed));
        if (a != null) {
            outAlias[0] = a;
            return;
        }
        Booking b = nearestBooking(lat, lon, openBookings(bookingDao.getWithGpsNote(), closed));
        if (b != null) {
            outBooking[0] = b;
            return;
        }
        outAlias[0] = nearestAlias(lat, lon, openAliases(correctionDao.getWithGps(0), closed));
    }

    /** Namen der geschlossenen Konten (läuft bereits auf dem Executor-Thread). */
    java.util.Set<String> closedAccounts() {
        return new java.util.HashSet<>(accountDao.getClosedNames());
    }

    /** True, wenn der Alias auf ein geschlossenes (Ziel-)Konto zeigt und daher nicht gewählt werden darf. */
    private static boolean aliasBlocked(PayeeCorrection a, java.util.Set<String> closed) {
        if (a == null || closed == null || closed.isEmpty()) {
            return false;
        }
        return closed.contains(a.account) || closed.contains(a.fromAccount) || closed.contains(a.toAccount);
    }

    private static List<Booking> openBookings(List<Booking> list, java.util.Set<String> closed) {
        if (closed == null || closed.isEmpty()) {
            return list;
        }
        List<Booking> out = new ArrayList<>();
        for (Booking b : list) {
            if (!closed.contains(b.account)) {
                out.add(b);
            }
        }
        return out;
    }

    private static List<PayeeCorrection> openAliases(List<PayeeCorrection> list, java.util.Set<String> closed) {
        if (closed == null || closed.isEmpty()) {
            return list;
        }
        List<PayeeCorrection> out = new ArrayList<>();
        for (PayeeCorrection a : list) {
            if (!aliasBlocked(a, closed)) {
                out.add(a);
            }
        }
        return out;
    }

    private PayeeCorrection nearestAlias(double lat, double lon, List<PayeeCorrection> list) {
        PayeeCorrection best = null;
        double bestD = de.spahr.ausgaben.location.Geo.RADIUS_M;
        for (PayeeCorrection a : list) {
            double d = nearestPointMeters(lat, lon, a);
            if (d <= bestD) {
                bestD = d;
                best = a;
            }
        }
        return best;
    }

    private Booking nearestBooking(double lat, double lon, List<Booking> list) {
        Booking best = null;
        double bestD = de.spahr.ausgaben.location.Geo.RADIUS_M;
        for (Booking b : list) {
            double[] ll = de.spahr.ausgaben.location.Geo.parse(b.note);
            if (ll == null) {
                continue;
            }
            double d = de.spahr.ausgaben.location.Geo.distanceMeters(lat, lon, ll[0], ll[1]);
            if (d <= bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    /** Speichert einen Alias autoritativ (Editor): ersetzt einen bestehenden mit gleichem
     * {@code spoken}+{@code corrected}; die im UI zusammengestellte Standortliste gilt (inkl. Löschungen). */
    void saveAlias(final PayeeCorrection alias) {
        saveAlias(alias, false);
    }

    /**
     * @param mergeGps beim Lernen ({@code true}): die Standorte des übergebenen Alias werden an die des
     *                 bestehenden Alias <b>angehängt</b> (Duplikate übersprungen), statt sie zu ersetzen.
     */
    void saveAlias(final PayeeCorrection alias, final boolean mergeGps) {
        if (alias == null) {
            return;
        }
        executor.execute(() -> {
            alias.spoken = alias.spoken == null ? "" : alias.spoken.trim().toLowerCase(Locale.GERMANY);
            alias.corrected = alias.corrected == null ? "" : alias.corrected.trim();
            if (alias.spoken.isEmpty() || alias.corrected.isEmpty()) {
                return;
            }
            if (alias.createdAt == 0) {
                alias.createdAt = System.currentTimeMillis();
            }
            java.util.List<double[]> points = alias.gpsPoints();
            if (mergeGps) {
                PayeeCorrection existing = correctionDao.findBySpokenCorrected(alias.spoken, alias.corrected);
                java.util.List<double[]> merged = existing != null ? existing.gpsPoints()
                        : new java.util.ArrayList<>();
                for (double[] p : points) {
                    if (!containsPoint(merged, p)) {
                        merged.add(p);
                    }
                }
                points = merged;
            }
            alias.setGpsPoints(points);
            correctionDao.upsert(alias);
        });
    }

    /** Ob {@code p} (auf ~5 Nachkommastellen) bereits in der Liste steht. */
    private static boolean containsPoint(java.util.List<double[]> list, double[] p) {
        for (double[] q : list) {
            if (Math.abs(q[0] - p[0]) < 1e-5 && Math.abs(q[1] - p[1]) < 1e-5) {
                return true;
            }
        }
        return false;
    }

    void getAllAliases(final Callback<List<PayeeCorrection>> callback) {
        executor.execute(() -> {
            final List<PayeeCorrection> result = correctionDao.getAll();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    void getAlias(final long id, final Callback<PayeeCorrection> callback) {
        executor.execute(() -> {
            final PayeeCorrection result = correctionDao.getById(id);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    void deleteAlias(final long id, final Runnable onDone) {
        executor.execute(() -> {
            correctionDao.deleteById(id);
            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }

    static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        return b == null ? "" : b.trim();
    }

    /** Hängt „GPS: lat, lon" an die Notiz an (gleiche Form wie am Phone); ersetzt einen alten GPS-Zusatz. */
    static String appendGps(String note, String coords) {
        if (coords == null || coords.trim().isEmpty()) {
            return note == null ? "" : note;
        }
        String base = (note == null ? "" : note).replaceAll("\\s*GPS:.*$", "").replaceAll("\\s+$", "");
        String tag = "GPS: " + coords.trim();
        return base.isEmpty() ? tag : base + " " + tag;
    }

    /**
     * Vorlage-Buchung per Empfänger suchen (exakter Teilstring, sonst unscharf). Gibt es mehrere Treffer
     * und ist die aktuelle Position {@code coords} bekannt, wird der zur Position nächstgelegene Treffer
     * mit GPS-Notiz gewählt – sonst die neueste Buchung.
     */
    private Booking findVoiceTemplate(String term, double[] coords, java.util.Set<String> closed) {
        if (term == null || term.isEmpty()) {
            return null;
        }
        Booking template = pickTemplate(openBookings(bookingDao.findByPayeeLike(term), closed), coords);
        if (template == null) {
            String best = de.spahr.ausgaben.voice.VoiceInput.bestFuzzyPayee(
                    term, bookingDao.getDistinctPayees());
            if (best != null) {
                template = pickTemplate(openBookings(bookingDao.findByPayeeLike(best), closed), coords);
            }
        }
        return template;
    }

    /** Aus den Treffern die zur Position nächste Buchung mit GPS-Notiz, sonst die erste (= neueste). */
    private Booking pickTemplate(List<Booking> matches, double[] coords) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        if (coords != null) {
            Booking near = nearestByNote(coords[0], coords[1], matches);
            if (near != null) {
                return near;
            }
        }
        return matches.get(0);
    }

    /**
     * Nächstgelegene Buchung mit GPS-Notiz aus der Liste – <b>ohne</b> 100‑m‑Deckel (der Empfänger wurde
     * explizit genannt, es soll der geografisch nächste unter den gleichnamigen Empfängern gewinnen).
     */
    private Booking nearestByNote(double lat, double lon, List<Booking> list) {
        Booking best = null;
        double bestD = Double.MAX_VALUE;
        for (Booking b : list) {
            double[] ll = de.spahr.ausgaben.location.Geo.parse(b.note);
            if (ll == null) {
                continue;
            }
            double d = de.spahr.ausgaben.location.Geo.distanceMeters(lat, lon, ll[0], ll[1]);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    /**
     * Löst einen gesprochenen Empfänger für die Sprach-Erfassung auf: zuerst über die bevorzugten Aliase,
     * dann über bestehende Buchungen, erst danach über die übrigen Aliase. Liefert Vorlage-Buchung und/oder
     * Alias sowie den – ggf. ersetzten – Empfänger für die Vorbelegung.
     */
    void resolveVoice(final String term, final String coords,
                      final Callback<VoiceResolution> callback) {
        executor.execute(() -> {
            String t = term == null ? "" : term.trim();
            Booking[] booking = new Booking[1];
            PayeeCorrection[] alias = new PayeeCorrection[1];
            resolve(t, de.spahr.ausgaben.location.Geo.parse(coords), closedAccounts(), booking, alias);
            String payee = alias[0] != null ? alias[0].corrected : t;
            final Booking fb = booking[0];
            final PayeeCorrection fa = alias[0];
            final String fp = payee;
            mainHandler.post(() -> callback.onResult(new VoiceResolution(fb, fa, fp)));
        });
    }

    /**
     * Auflösung für die Betrag-only-Erfassung am Phone: sucht am Standort {@code coords} („lat, lon") eine
     * Vorlage (bevorzugte Aliase → Buchungen → übrige Aliase, 100 m). Ohne Standort/Treffer bleibt alles
     * leer → Editor wird nur mit dem Betrag geöffnet.
     */
    void resolveVoiceByGps(final String coords, final Callback<VoiceResolution> callback) {
        executor.execute(() -> {
            Booking[] booking = new Booking[1];
            PayeeCorrection[] alias = new PayeeCorrection[1];
            double[] ll = de.spahr.ausgaben.location.Geo.parse(coords);
            if (ll != null) {
                resolveGps(ll[0], ll[1], closedAccounts(), booking, alias);
            }
            String payee = alias[0] != null ? alias[0].corrected
                    : (booking[0] != null ? booking[0].payee : "");
            final Booking fb = booking[0];
            final PayeeCorrection fa = alias[0];
            final String fp = payee;
            mainHandler.post(() -> callback.onResult(new VoiceResolution(fb, fa, fp)));
        });
    }
}
