package de.spahr.ausgaben.net;

import java.io.IOException;
import java.util.List;

import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Ordner-/datei-orientierter Zugriff auf ein entferntes Sync-Ziel. Implementierungen kapseln die
 * Zugangsdaten; {@code folder} ist relativ zur konfigurierten Wurzel (WebDAV-Wurzel bzw. SMB-Freigabe/Basis).
 * Alle Methoden blockieren (Netzwerk) und werden von den Aufrufern auf Hintergrund-Threads genutzt.
 */
public interface RemoteStorage {

    void uploadText(String folder, String fileName, String content) throws IOException;

    void uploadBytes(String folder, String fileName, byte[] content) throws IOException;

    /** Dateinamen im Ordner mit der Endung {@code ext} (ohne Punkt, z. B. "csv" oder "kmy"). */
    List<String> listFiles(String folder, String ext) throws IOException;

    /** Unterordner-Namen im Ordner (für den Datei-Browser); leere Liste, wenn nicht unterstützt. */
    default List<String> listFolders(String folder) throws IOException {
        return java.util.Collections.emptyList();
    }

    String downloadText(String folder, String fileName) throws IOException;

    byte[] downloadBytes(String folder, String fileName) throws IOException;

    /** Prüft die Verbindung (Verbinden + Auflisten der Basis); wirft bei Fehler eine {@link IOException}. */
    void testConnection() throws IOException;

    /** Erstellt das passende Backend nach Server-Typ; Zugangsdaten aus den Einstellungen. */
    static RemoteStorage from(SettingsStore s) {
        return from(s.getServerType(), s.getUrl(), s.getUser(), s.getPassword());
    }

    /**
     * Wie {@link #from(SettingsStore)}, aber aus Einzelwerten – für UI-Aktionen mit noch nicht
     * gespeicherten Feldwerten (Verbindung testen / .kmy durchsuchen).
     */
    static RemoteStorage from(String serverType, String url, String user, String password) {
        if (SettingsStore.SERVER_SMB.equals(serverType)) {
            return new SmbStorage(url, user, password);
        }
        boolean nextcloudLayout = !SettingsStore.SERVER_WEBDAV.equals(serverType);
        return new WebDavStorage(url, user, password, nextcloudLayout);
    }
}
