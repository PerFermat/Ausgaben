package de.spahr.ausgaben.net.smb;

import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Gemeinsame Bausteine für alle SMB-Verbindungen: Client-Konfiguration und die Umsetzung von
 * „Benutzer + Passwort" in einen Anmeldekontext (leerer Benutzer = Gast/anonym, {@code DOMÄNE\Benutzer}
 * = Windows-Domäne).
 *
 * <p>smbj ist ab Werk sparsam konfiguriert: {@code encryptData} und {@code dfsEnabled} sind aus. Beides
 * schaltet die App ein, weil es sonst ganze Server-Konfigurationen ausschließt – siehe
 * {@link #configure(long, long)}.</p>
 */
public final class SmbSessions {

    private SmbSessions() {
    }

    /**
     * Client-Konfiguration der App. Zwei Abweichungen von den smbj-Voreinstellungen:
     *
     * <ul>
     *   <li><b>Verschlüsselung anbieten</b> ({@code withEncryptData}). Ohne diese Zusage sendet smbj
     *       bei SMB 3.1.1 keinen {@code SMB2EncryptionCapabilities}-Kontext, handelt also keinen Cipher
     *       aus – und ignoriert danach ein {@code SMB2_SESSION_FLAG_ENCRYPT_DATA} des Servers, schickt
     *       also weiter Klartext. Eine Freigabe mit {@code smb encrypt = required} wäre unbenutzbar.
     *       Verschlüsselt wird trotzdem nur, wenn der Server es verlangt – smbj setzt das Sitzungs-Flag
     *       ausschließlich in genau diesem Fall.</li>
     *   <li><b>DFS</b> ({@code withDfsEnabled}). Für {@code msdfs root}-Freigaben und Windows-DFS-
     *       Namespaces; bei gewöhnlichen Freigaben ändert sich nichts, smbj löst Referrals erst nach
     *       {@code STATUS_PATH_NOT_COVERED} auf.</li>
     * </ul>
     *
     * <p>BouncyCastle statt JCE, weil Android weder AES-CMAC (Signierung) noch AES-GCM/CCM in der von
     * smbj erwarteten Form mitbringt.</p>
     */
    private static SmbConfig configure(long timeoutSeconds, long soTimeoutSeconds) {
        return SmbConfig.builder()
                .withSecurityProvider(new BCSecurityProvider())
                .withEncryptData(true)
                .withDfsEnabled(true)
                .withTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .withSoTimeout(soTimeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    /** Wie {@link #configure(long, long)}, aber ohne Verschlüsselung/DFS – Rückfall für alte Server. */
    private static SmbConfig configurePlain(long timeoutSeconds, long soTimeoutSeconds) {
        return SmbConfig.builder()
                .withSecurityProvider(new BCSecurityProvider())
                .withTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .withSoTimeout(soTimeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    /** Neuer Client mit den in der App üblichen Zeitüberschreitungen. */
    public static SMBClient client() {
        return new SMBClient(configure(30, 45));
    }

    /** Wie {@link #client()}, nur mit kurzen Zeiten – für die Einrichtung, damit nichts lange hängt. */
    public static SMBClient quickClient() {
        return new SMBClient(configure(8, 12));
    }

    /**
     * Verbindung samt zugehörigem Client: beide gehören zusammen und werden gemeinsam geschlossen.
     * {@link #plainFallback} sagt, ob erst der zweite Versuch ohne Verschlüsselungs-/DFS-Zusage
     * geklappt hat – das gehört in den Diagnosebericht.
     */
    public static final class Link implements Closeable {
        public final SMBClient client;
        public final Connection connection;
        public final boolean plainFallback;
        /** Port, über den die Verbindung wirklich zustande kam (445, wenn ausgewichen wurde). */
        public final int usedPort;

        Link(SMBClient client, Connection connection, boolean plainFallback, int usedPort) {
            this.client = client;
            this.connection = connection;
            this.plainFallback = plainFallback;
            this.usedPort = usedPort;
        }

        @Override
        public void close() {
            try {
                connection.close();
            } catch (Exception ignored) {
                // Beim Aufräumen zählt nur, dass der Client danach zu ist.
            } finally {
                client.close();
            }
        }
    }

    /** Der SMB-Standardport; ein leeres Portfeld und die 445 bedeuten dasselbe. */
    public static final int DEFAULT_PORT = 445;
    /** Wartezeit der Port-Vorprüfung: lang genug fürs LAN, kurz genug, um nicht zu stören. */
    private static final int PROBE_TIMEOUT_MS = 1500;

    /** Prüft, ob auf {@code host:port} überhaupt jemand horcht – für Tests austauschbar. */
    public interface PortProbe {
        boolean reachable(String host, int port);
    }

    /**
     * Welchen Port wir wirklich ansprechen. Ein abweichender Port stammt oft nicht vom Benutzer,
     * sondern aus der mDNS-Auskunft des Servers – und die ist nachweislich manchmal falsch. Antwortet
     * dort niemand, wohl aber auf 445, gilt 445. Geprüft wird <b>vorab</b> per TCP (statt es einfach
     * zu versuchen), weil ein gefilterter Port sonst jedes Mal in die volle Zeitüberschreitung liefe.
     */
    static int choosePort(String host, int configured, PortProbe probe) {
        if (configured <= 0 || configured == DEFAULT_PORT) {
            return configured;
        }
        if (probe.reachable(host, configured)) {
            return configured;
        }
        return probe.reachable(host, DEFAULT_PORT) ? DEFAULT_PORT : configured;
    }

    /** Vorprüfung per TCP-Verbindungsversuch (wie die Suche im Netz sie schon benutzt). */
    private static boolean tcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verbindet sich mit dem Server; {@code port} kleiner 1 bedeutet den Standardport 445. Scheitert
     * der Aufbau, folgt <b>ein</b> Versuch ohne die Verschlüsselungs-/DFS-Zusage: Es gibt Geräte, die
     * an den zusätzlichen Negotiate-Kontexten hängenbleiben, und die sollen nicht schlechter dastehen
     * als vor deren Einführung. Bleibt auch der erfolglos, gilt der Fehler des ersten Versuchs.
     */
    public static Link open(String host, int port, boolean quick) throws IOException {
        int target = choosePort(host, port, SmbSessions::tcpReachable);
        SMBClient client = new SMBClient(quick ? configure(8, 12) : configure(30, 45));
        try {
            return new Link(client, connect(client, host, target), false, target);
        } catch (Exception first) {
            client.close();
            SMBClient plain = new SMBClient(quick ? configurePlain(8, 12) : configurePlain(30, 45));
            try {
                return new Link(plain, connect(plain, host, target), true, target);
            } catch (Exception ignored) {
                plain.close();
                throw first instanceof IOException ? (IOException) first
                        : new IOException(first.getMessage() == null ? first.toString()
                                : first.getMessage(), first);
            }
        }
    }

    /**
     * Verbindung zum Server; {@code port} kleiner 1 bedeutet den SMB-Standardport 445. Lauscht der
     * Server woanders, steht die Portnummer in der Adresse ({@code smb://Host:Port/Freigabe}).
     */
    public static Connection connect(SMBClient client, String host, int port) throws IOException {
        return port > 0 ? client.connect(host, port) : client.connect(host);
    }

    /** Der reine Benutzername ohne {@code DOMÄNE\}-Präfix; leer heißt „kein Benutzer angegeben". */
    static String userNameOf(String user) {
        String u = user == null ? "" : user.trim();
        int sep = u.indexOf('\\');
        if (sep < 0) {
            sep = u.indexOf('/');
        }
        return sep < 0 ? u : u.substring(sep + 1).trim();
    }

    /**
     * Darf der Server die Sitzung auf <em>Gast</em> herunterstufen, ohne dass das ein Fehler ist?
     *
     * <p>Ja, solange <b>kein Passwort</b> eingegeben wurde: Bei einer passwortlosen Freigabe ist Gast
     * genau das Gewünschte. Verdächtig ist die Herabstufung nur, wenn jemand ein Passwort getippt hat –
     * Samba mit {@code map to guest = bad password} nimmt nämlich auch ein <b>falsches</b> Passwort an
     * und stuft still auf Gast herunter; die Anmeldung „gelingt", erst der Dateizugriff scheitert
     * später mit einem nichtssagenden {@code STATUS_ACCESS_DENIED}.</p>
     */
    static boolean guestAllowed(String user, String password) {
        return userNameOf(user).isEmpty() || password == null || password.isEmpty();
    }

    /**
     * Meldet sich an: ohne Benutzernamen als Gast und – falls der Server das ablehnt – anonym
     * (Null-Sitzung), denn manche Freigaben stehen nur so offen. Mit Benutzernamen und Passwort wird
     * eine stille Herabstufung auf Gast als Fehlanmeldung gemeldet (siehe {@link #guestAllowed}).
     */
    public static Session authenticate(Connection connection, String user, String password)
            throws IOException {
        // Ein reines „GRUPPE\" aus der Domänen-Vorbelegung des Assistenten ist kein Benutzername.
        String u = userNameOf(user).isEmpty() ? "" : user.trim();
        if (u.isEmpty()) {
            try {
                return connection.authenticate(AuthenticationContext.guest());
            } catch (Exception asGuest) {
                try {
                    return connection.authenticate(AuthenticationContext.anonymous());
                } catch (Exception ignored) {
                    throw asGuest instanceof IOException ? (IOException) asGuest
                            : new IOException(asGuest.getMessage() == null ? asGuest.toString()
                                    : asGuest.getMessage(), asGuest);
                }
            }
        }
        Session session = connection.authenticate(authFor(u, password));
        if (session.isGuest() && !guestAllowed(u, password)) {
            try {
                session.close();
            } catch (Exception ignored) {
                // Die Sitzung ist ohnehin unbrauchbar.
            }
            throw new IOException("STATUS_LOGON_FAILURE: Der Server hat die Anmeldung als Gast"
                    + " behandelt – Benutzername oder Passwort stimmen nicht.");
        }
        return session;
    }

    /** Anmeldekontext: leerer Benutzer → Gast, sonst Benutzer/Passwort mit optionaler Domäne. */
    public static AuthenticationContext authFor(String user, String password) {
        String u = user == null ? "" : user.trim();
        String pw = password == null ? "" : password;
        String domain = null;
        int sep = u.indexOf('\\');
        if (sep < 0) {
            sep = u.indexOf('/');
        }
        if (sep >= 0) {
            domain = u.substring(0, sep);
            u = u.substring(sep + 1);
        }
        if (u.isEmpty()) {
            return AuthenticationContext.guest();
        }
        return new AuthenticationContext(u, pw.toCharArray(), domain);
    }
}
