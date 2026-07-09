package de.spahr.ausgaben.net;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.spahr.ausgaben.settings.SettingsStore;

/**
 * {@link RemoteStorage} über eine SMB/Samba-Freigabe (SMB2/3, via smbj). Die „URL" hat die Form
 * {@code smb://Host/Freigabe[/Basis]}. Benutzer/Passwort aus den Einstellungen; leerer Benutzer → Gast.
 * Ein {@code DOMÄNE\\Benutzer}-Präfix wird als Windows-Domäne interpretiert. Verbindung wird je Aufruf
 * geöffnet und wieder geschlossen (wie der WebDAV-Pfad zustandslos).
 */
public class SmbStorage implements RemoteStorage {

    private final String host;
    private final String share;
    private final String base;
    private final String domain;
    private final String user;
    private final String password;

    public SmbStorage(String url, String user, String password) {
        String[] parts = SettingsStore.parseSmb(url);
        this.host = parts[0];
        this.share = parts[1];
        this.base = parts[2];
        String u = user == null ? "" : user.trim();
        int sep = u.indexOf('\\');
        if (sep < 0) {
            sep = u.indexOf('/');
        }
        if (sep >= 0) {
            this.domain = u.substring(0, sep);
            this.user = u.substring(sep + 1);
        } else {
            this.domain = null;
            this.user = u;
        }
        this.password = password == null ? "" : password;
    }

    private interface Action<T> {
        T run(DiskShare share) throws Exception;
    }

    private <T> T withShare(Action<T> action) throws IOException {
        if (host.isEmpty() || share.isEmpty()) {
            throw new IOException("SMB: Host/Freigabe fehlt (smb://Host/Freigabe)");
        }
        SmbConfig config = SmbConfig.builder()
                .withSecurityProvider(new BCSecurityProvider())
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(45, TimeUnit.SECONDS)
                .build();
        SMBClient client = new SMBClient(config);
        try (Connection connection = client.connect(host)) {
            AuthenticationContext auth = user.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(user, password.toCharArray(), domain);
            Session session = connection.authenticate(auth);
            try (DiskShare disk = (DiskShare) session.connectShare(share)) {
                return action.run(disk);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    @Override
    public void uploadText(String folder, String fileName, String content) throws IOException {
        uploadBytes(folder, fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void uploadBytes(String folder, String fileName, byte[] content) throws IOException {
        final String dir = joinPath(base, folder);
        final String path = joinPath(dir, fileName);
        withShare(disk -> {
            ensureDir(disk, dir);
            try (com.hierynomus.smbj.share.File f = disk.openFile(path,
                    EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF, null);
                 OutputStream os = f.getOutputStream()) {
                os.write(content);
            }
            return null;
        });
    }

    @Override
    public List<String> listFiles(String folder, String ext) throws IOException {
        final String dir = joinPath(base, folder);
        final String suffix = "." + ext.toLowerCase();
        return withShare(disk -> {
            List<String> names = new ArrayList<>();
            long dirFlag = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue();
            for (FileIdBothDirectoryInformation info : disk.list(dir)) {
                String name = info.getFileName();
                if (name == null || name.equals(".") || name.equals("..")) {
                    continue;
                }
                if ((info.getFileAttributes() & dirFlag) != 0) {
                    continue;
                }
                if (name.toLowerCase().endsWith(suffix)) {
                    names.add(name);
                }
            }
            return names;
        });
    }

    @Override
    public List<String> listFolders(String folder) throws IOException {
        final String dir = joinPath(base, folder);
        return withShare(disk -> {
            List<String> names = new ArrayList<>();
            long dirFlag = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue();
            for (FileIdBothDirectoryInformation info : disk.list(dir)) {
                String name = info.getFileName();
                if (name == null || name.equals(".") || name.equals("..")) {
                    continue;
                }
                if ((info.getFileAttributes() & dirFlag) != 0) {
                    names.add(name);
                }
            }
            return names;
        });
    }

    @Override
    public String downloadText(String folder, String fileName) throws IOException {
        return new String(downloadBytes(folder, fileName), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] downloadBytes(String folder, String fileName) throws IOException {
        final String path = joinPath(joinPath(base, folder), fileName);
        return withShare(disk -> {
            try (com.hierynomus.smbj.share.File f = disk.openFile(path,
                    EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN, null);
                 InputStream is = f.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            }
        });
    }

    @Override
    public void testConnection() throws IOException {
        withShare(disk -> disk.list(base));
    }

    /** Legt fehlende Verzeichnisse der (Backslash-)Pfadkette an; „schon vorhanden" wird ignoriert. */
    private void ensureDir(DiskShare disk, String dir) {
        if (dir.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String seg : dir.split("\\\\")) {
            if (seg.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\\');
            }
            sb.append(seg);
            String sofar = sb.toString();
            try {
                if (!disk.folderExists(sofar)) {
                    disk.mkdir(sofar);
                }
            } catch (Exception ignored) {
                // Rennen/Rechte: der eigentliche openFile-Aufruf meldet einen echten Fehler.
            }
        }
    }

    /** Verbindet zwei Pfadteile zu einem SMB-Pfad (Backslash-getrennt, ohne führende/mehrfache Trenner). */
    private static String joinPath(String a, String b) {
        String left = normalize(a);
        String right = normalize(b);
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + "\\" + right;
    }

    /** Normalisiert einen Pfadteil: Slashes → Backslash, Trenner am Rand/doppelt entfernen. */
    private static String normalize(String p) {
        if (p == null) {
            return "";
        }
        String s = p.trim().replace('/', '\\');
        StringBuilder out = new StringBuilder();
        for (String seg : s.split("\\\\")) {
            if (seg.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\\');
            }
            out.append(seg);
        }
        return out.toString();
    }
}
