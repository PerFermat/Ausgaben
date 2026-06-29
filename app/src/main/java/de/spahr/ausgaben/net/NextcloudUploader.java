package de.spahr.ausgaben.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Lädt eine Datei per WebDAV (HTTP PUT) auf eine Nextcloud-Instanz hoch. */
public class NextcloudUploader {

    private static final MediaType CSV = MediaType.parse("text/csv; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

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

    private String buildUrl(String baseUrl, String user, String folder, String fileName) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        StringBuilder sb = new StringBuilder(base)
                .append("/remote.php/dav/files/")
                .append(encodePath(user));

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
