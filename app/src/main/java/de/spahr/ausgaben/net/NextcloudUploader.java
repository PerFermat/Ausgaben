package de.spahr.ausgaben.net;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * WebDAV-Client: Hochladen (PUT), Auflisten (PROPFIND) und Herunterladen (GET).
 * Unterstützt das Nextcloud-Layout ({@code /remote.php/dav/files/<user>/…}) sowie generisches WebDAV,
 * bei dem die eingetragene Basis-URL bereits die DAV-Wurzel ist.
 */
public class NextcloudUploader {

    private static final MediaType CSV = MediaType.parse("text/csv; charset=utf-8");
    private static final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private static final MediaType OCTET = MediaType.parse("application/octet-stream");

    private final OkHttpClient client = new OkHttpClient();
    /** true = Nextcloud-Pfadschema, false = generisches WebDAV (Basis-URL ist die Wurzel). */
    private final boolean nextcloudLayout;

    public NextcloudUploader() {
        this(true);
    }

    public NextcloudUploader(boolean nextcloudLayout) {
        this.nextcloudLayout = nextcloudLayout;
    }

    /**
     * Lädt {@code content} unter {@code fileName} hoch.
     *
     * @throws IOException bei Netzwerk- oder HTTP-Fehlern (Status != 2xx)
     */
    public void upload(String baseUrl, String user, String password, String folder,
                       String fileName, String content) throws IOException {
        String url = buildUrl(baseUrl, user, folder, fileName);

        RequestBody body = RequestBody.create(content.getBytes(StandardCharsets.UTF_8), CSV);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, password))
                .put(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
        }
    }

    /** Lädt {@code content} (Rohbytes) unter {@code fileName} hoch, z. B. eine gepackte .kmy. */
    public void uploadBytes(String baseUrl, String user, String password, String folder,
                            String fileName, byte[] content) throws IOException {
        String url = buildUrl(baseUrl, user, folder, fileName);
        RequestBody body = RequestBody.create(content, OCTET);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, password))
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
        }
    }

    /**
     * Listet die CSV-Dateinamen im angegebenen Nextcloud-Ordner per WebDAV-PROPFIND.
     *
     * @throws IOException bei Netzwerk-/HTTP-Fehlern
     */
    public List<String> listCsvFiles(String baseUrl, String user, String password, String folder)
            throws IOException {
        return listFiles(baseUrl, user, password, folder, "csv");
    }

    /** Listet die Dateinamen mit der angegebenen Endung (ohne Punkt, z. B. "csv" oder "kmy"). */
    public List<String> listFiles(String baseUrl, String user, String password, String folder,
                                  String ext) throws IOException {
        String url = buildFolderUrl(baseUrl, user, folder);
        String body = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\">"
                + "<d:prop><d:resourcetype/></d:prop></d:propfind>";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, password))
                .header("Depth", "1")
                .method("PROPFIND", RequestBody.create(body, XML))
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody rb = response.body();
            String xml = rb == null ? "" : rb.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return parseNames(xml, ext);
        }
    }

    /** Lädt den Textinhalt einer Datei aus dem Ordner herunter. */
    public String downloadText(String baseUrl, String user, String password, String folder,
                               String fileName) throws IOException {
        String url = buildUrl(baseUrl, user, folder, fileName);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, password))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody rb = response.body();
            String content = rb == null ? "" : rb.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return content;
        }
    }

    /** Lädt die Rohbytes einer Datei aus dem Ordner herunter (z. B. eine gepackte .kmy). */
    public byte[] downloadBytes(String baseUrl, String user, String password, String folder,
                                String fileName) throws IOException {
        String url = buildUrl(baseUrl, user, folder, fileName);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, password))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody rb = response.body();
            byte[] content = rb == null ? new byte[0] : rb.bytes();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return content;
        }
    }

    /** Extrahiert aus der Multistatus-Antwort die Dateinamen mit der Endung {@code ext} (ohne Collections). */
    private List<String> parseNames(String xml, String ext) throws IOException {
        String suffix = "." + ext.toLowerCase();
        List<String> names = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            String currentHref = null;
            boolean isCollection = false;
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (event == XmlPullParser.START_TAG && name != null) {
                    String local = localName(name);
                    if (local.equals("response")) {
                        currentHref = null;
                        isCollection = false;
                    } else if (local.equals("href")) {
                        currentHref = parser.nextText();
                    } else if (local.equals("collection")) {
                        isCollection = true;
                    }
                } else if (event == XmlPullParser.END_TAG && "response".equals(localName(name))) {
                    if (currentHref != null && !isCollection) {
                        String fileName = lastSegment(currentHref);
                        if (fileName.toLowerCase().endsWith(suffix)) {
                            names.add(fileName);
                        }
                    }
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException("Antwort konnte nicht gelesen werden", e);
        }
        return names;
    }

    private String localName(String qName) {
        if (qName == null) {
            return "";
        }
        int i = qName.indexOf(':');
        return (i >= 0 ? qName.substring(i + 1) : qName).toLowerCase();
    }

    private String lastSegment(String href) {
        String h = href;
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        int slash = h.lastIndexOf('/');
        String seg = slash >= 0 ? h.substring(slash + 1) : h;
        try {
            return URLDecoder.decode(seg, "UTF-8");
        } catch (Exception e) {
            return seg;
        }
    }

    /**
     * WebDAV-Wurzel: bei Nextcloud {@code <base>/remote.php/dav/files/<user>}, bei generischem WebDAV die
     * eingetragene Basis-URL selbst (der Nutzer gibt dort die vollständige DAV-Wurzel an).
     */
    private String rootUrl(String baseUrl, String user) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (nextcloudLayout) {
            return base + "/remote.php/dav/files/" + encodePath(user);
        }
        return base;
    }

    /** Hängt die (bereinigten) Ordner-Segmente an den Builder an. */
    private void appendFolder(StringBuilder sb, String folder) {
        String cleanFolder = folder == null ? "" : folder.trim();
        while (cleanFolder.startsWith("/")) {
            cleanFolder = cleanFolder.substring(1);
        }
        while (cleanFolder.endsWith("/")) {
            cleanFolder = cleanFolder.substring(0, cleanFolder.length() - 1);
        }
        if (!cleanFolder.isEmpty()) {
            for (String part : cleanFolder.split("/")) {
                if (!part.isEmpty()) {
                    sb.append("/").append(encodePath(part));
                }
            }
        }
    }

    private String buildFolderUrl(String baseUrl, String user, String folder) {
        StringBuilder sb = new StringBuilder(rootUrl(baseUrl, user));
        appendFolder(sb, folder);
        sb.append("/");
        return sb.toString();
    }

    private String buildUrl(String baseUrl, String user, String folder, String fileName) {
        StringBuilder sb = new StringBuilder(rootUrl(baseUrl, user));
        appendFolder(sb, folder);
        sb.append("/").append(encodePath(fileName));
        return sb.toString();
    }

    /** URL-kodiert einen Pfadabschnitt, lässt aber Slashes unberührt (werden separat gesetzt). */
    private String encodePath(String segment) {
        StringBuilder out = new StringBuilder();
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }
}
