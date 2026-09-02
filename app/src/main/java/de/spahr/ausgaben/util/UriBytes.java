package de.spahr.ausgaben.util;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Eine ausgewählte Datei am Stück einlesen.
 *
 * <p>Was der Dateiwähler liefert, ist eine {@link Uri} und keine Datei — dahinter kann eine Cloud, ein
 * anderer App-Speicher oder ein USB-Stick stehen. Gelesen wird deshalb über den ContentResolver, und weil
 * das an vier Stellen der Oberfläche gebraucht wurde (Sicherung einspielen in zwei Masken, Sprachdatei,
 * Vorlagenprüfung), stand dieselbe Schleife dort viermal.</p>
 */
public final class UriBytes {

    private UriBytes() {
    }

    /** Der ganze Inhalt. Wirft, wenn sich die Datei nicht öffnen oder lesen lässt. */
    public static byte[] read(Context context, Uri uri) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (is != null && (n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }
}
