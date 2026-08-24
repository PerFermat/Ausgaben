package de.spahr.ausgaben.pdf;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Holt den Text samt Wortpositionen aus einem PDF. Einziger Ort der App, der PdfBox kennt — alles
 * Weitere arbeitet auf {@link PdfText} und braucht dafür keine PDF-Datei.
 *
 * <p>Android bringt das nicht mit: {@code PdfRenderer} kann eine Seite nur als Bild zeichnen. Deshalb
 * die Bibliothek (PdfBox-Android, Apache 2.0) — sie arbeitet rein lokal, es geht nichts ins Netz.</p>
 *
 * <p><b>Nicht im Hauptthread aufrufen.</b> Ein mehrseitiges Dokument braucht spürbar Zeit; die Aufrufer
 * laufen über den Executor des Repositories.</p>
 */
public final class PdfTextExtractor {

    /** PdfBox lädt seine Schriftmetriken aus den Assets und will dafür einmalig einen Context. */
    private static volatile boolean initialized;

    private PdfTextExtractor() {
    }

    /**
     * Liest das PDF hinter {@code uri}.
     *
     * @return der Text; {@link PdfText#hasText()} ist {@code false}, wenn das Dokument nur aus Bildern
     *         besteht (eingescannt) — dann ist ohne Texterkennung nichts zu holen
     * @throws IOException wenn die Datei nicht lesbar oder kein PDF ist; auch bei einem
     *                     passwortgeschützten Dokument
     */
    public static PdfText read(Context context, Uri uri) throws IOException {
        ensureInitialized(context);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Datei nicht lesbar: " + uri);
            }
            return read(in);
        }
    }

    /** Wie oben, aber aus einem offenen Strom — der Strom wird nicht geschlossen. */
    public static PdfText read(InputStream in) throws IOException {
        final PdfText.Builder builder = new PdfText.Builder();
        try (PDDocument document = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    collect(builder, getCurrentPageNo() - 1, positions);
                }
            };
            stripper.setSortByPosition(true);
            // Der zurückgegebene Text interessiert nicht; gebraucht wird, was writeString einsammelt.
            stripper.getText(document);
        }
        return builder.build();
    }

    /**
     * Zerlegt ein Textstück in Wörter. PdfBox liefert die Zeichen einzeln mit Position; getrennt wird
     * am Leerzeichen, und jedes Wort behält die linke Kante seines ersten und die rechte Kante seines
     * letzten Zeichens.
     */
    private static void collect(PdfText.Builder builder, int page, List<TextPosition> positions) {
        StringBuilder word = new StringBuilder();
        float startX = 0;
        float endX = 0;
        float y = 0;
        for (TextPosition p : positions) {
            String s = p.getUnicode();
            if (s == null) {
                continue;
            }
            if (s.trim().isEmpty()) {
                flush(builder, page, word, startX, endX, y);
                continue;
            }
            if (word.length() == 0) {
                startX = p.getXDirAdj();
                y = p.getYDirAdj();
            }
            word.append(s);
            endX = p.getXDirAdj() + p.getWidthDirAdj();
        }
        flush(builder, page, word, startX, endX, y);
    }

    private static void flush(PdfText.Builder builder, int page, StringBuilder word,
                              float startX, float endX, float y) {
        if (word.length() > 0) {
            builder.add(page, word.toString(), startX, endX, y);
            word.setLength(0);
        }
    }

    private static void ensureInitialized(Context context) {
        if (!initialized) {
            synchronized (PdfTextExtractor.class) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(context.getApplicationContext());
                    initialized = true;
                }
            }
        }
    }
}
