package de.spahr.ausgaben.net;

/**
 * Kleine Pfadhelfer für die serverseitigen Ordnerangaben (relativ zur WebDAV-Wurzel bzw. SMB-Freigabe).
 * Bis dahin stand dasselbe Muster in mehreren Koordinatoren wortgleich herum.
 */
public final class RemotePath {

    private RemotePath() {
    }

    /** Der Ordnerteil eines Pfades: alles vor dem letzten {@code /}, sonst leer. */
    public static String folderOf(String path) {
        String p = path == null ? "" : path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    /** Der Dateiname eines Pfades: alles nach dem letzten {@code /}. */
    public static String fileOf(String path) {
        String p = path == null ? "" : path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }

    /** Hängt {@code sub} an {@code base} an; leere Teile fallen weg, doppelte Schrägstriche entstehen nicht. */
    public static String join(String base, String sub) {
        String b = base == null ? "" : base.trim();
        String s = sub == null ? "" : sub.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (b.isEmpty()) {
            return s;
        }
        return s.isEmpty() ? b : b + "/" + s;
    }
}
