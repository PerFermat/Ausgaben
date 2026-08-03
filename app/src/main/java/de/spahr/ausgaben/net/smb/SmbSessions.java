package de.spahr.ausgaben.net.smb;

import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Gemeinsame Bausteine für alle SMB-Verbindungen: Client-Konfiguration und die Umsetzung von
 * „Benutzer + Passwort" in einen Anmeldekontext (leerer Benutzer = Gast, {@code DOMÄNE\Benutzer} =
 * Windows-Domäne).
 */
public final class SmbSessions {

    private SmbSessions() {
    }

    /** Neuer Client mit den in der App üblichen Zeitüberschreitungen (BouncyCastle statt JCE). */
    public static SMBClient client() {
        SmbConfig config = SmbConfig.builder()
                .withSecurityProvider(new BCSecurityProvider())
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(45, TimeUnit.SECONDS)
                .build();
        return new SMBClient(config);
    }

    /** Wie {@link #client()}, nur mit kurzen Zeiten – für die Einrichtung, damit nichts lange hängt. */
    public static SMBClient quickClient() {
        SmbConfig config = SmbConfig.builder()
                .withSecurityProvider(new BCSecurityProvider())
                .withTimeout(8, TimeUnit.SECONDS)
                .withSoTimeout(12, TimeUnit.SECONDS)
                .build();
        return new SMBClient(config);
    }

    /**
     * Verbindung zum Server; {@code port} kleiner 1 bedeutet den SMB-Standardport 445. Lauscht der
     * Server woanders, steht die Portnummer in der Adresse ({@code smb://Host:Port/Freigabe}).
     */
    public static Connection connect(SMBClient client, String host, int port) throws IOException {
        return port > 0 ? client.connect(host, port) : client.connect(host);
    }

    /**
     * Meldet sich an und deckt dabei den stillen Gast-Rückfall auf: Samba mit
     * {@code map to guest = bad password} nimmt ein falsches Passwort an und stuft die Sitzung auf
     * <em>Gast</em> herunter. Die Anmeldung „gelingt" dann, das Auflisten der Freigaben auch – erst der
     * Dateizugriff scheitert mit einem nichtssagenden {@code STATUS_ACCESS_DENIED}. Wer einen Benutzer
     * angegeben hat, will kein Gast sein; deshalb hier abbrechen.
     */
    public static Session authenticate(Connection connection, String user, String password)
            throws IOException {
        Session session = connection.authenticate(authFor(user, password));
        if (user != null && !user.trim().isEmpty() && session.isGuest()) {
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
