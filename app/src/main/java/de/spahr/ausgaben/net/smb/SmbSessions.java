package de.spahr.ausgaben.net.smb;

import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;

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
