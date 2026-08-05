package de.spahr.ausgaben.receipt;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Rechenkern der Belegbearbeitung: Zuschneiden, Begradigen schräg fotografierter Rechnungen und
 * Helligkeit/Kontrast. Kommt ohne Zusatzbibliothek aus – die Trapezkorrektur leistet
 * {@link Matrix#setPolyToPoly}, die Tonwertkorrektur eine {@link ColorMatrix}.
 *
 * <p>Der Zuschneiderahmen ist ein Viereck aus vier Punkten in <b>Bildkoordinaten</b>, im Uhrzeigersinn ab
 * oben-links: {@code [x0,y0, x1,y1, x2,y2, x3,y3]}. Bis auf {@link #apply} ist alles reine Rechnung ohne
 * Android-Abhängigkeit und damit direkt testbar.</p>
 */
public final class ReceiptEdit {

    /** Ecken des Vierecks (Reihenfolge wie im Punkte-Feld). */
    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;

    /** Kleinster Abstand zweier gegenüberliegender Kanten in Pixeln – verhindert einen entarteten Rahmen. */
    private static final float MIN_SIDE = 16f;

    private ReceiptEdit() {
    }

    /** Startrahmen: das ganze Bild, 5 % vom Rand eingerückt. */
    public static float[] startQuad(int w, int h) {
        float ix = w * 0.05f;
        float iy = h * 0.05f;
        return new float[]{ix, iy, w - ix, iy, w - ix, h - iy, ix, h - iy};
    }

    /**
     * Setzt die Ecke {@code corner} auf ({@code x}, {@code y}), geklemmt aufs Bild. Im Rechteck-Modus ziehen
     * die beiden Nachbarecken mit, damit der Rahmen achsparallel bleibt; im Trapez-Modus bewegt sich nur die
     * angefasste Ecke, sodass sich auch eine schräg liegende Rechnung einfassen lässt.
     */
    public static void moveCorner(float[] quad, int corner, float x, float y,
                                  boolean rectMode, int w, int h) {
        x = clamp(x, 0, w);
        y = clamp(y, 0, h);
        if (!rectMode) {
            quad[corner * 2] = x;
            quad[corner * 2 + 1] = y;
            return;
        }
        // Achsparallel: die Ecke gegenüber bleibt stehen, die beiden Nachbarn übernehmen je eine Koordinate.
        int opposite = (corner + 2) % 4;
        float ox = quad[opposite * 2];
        float oy = quad[opposite * 2 + 1];
        x = keepAway(x, ox, w);
        y = keepAway(y, oy, h);
        float left = Math.min(x, ox);
        float right = Math.max(x, ox);
        float top = Math.min(y, oy);
        float bottom = Math.max(y, oy);
        quad[TOP_LEFT * 2] = left;
        quad[TOP_LEFT * 2 + 1] = top;
        quad[TOP_RIGHT * 2] = right;
        quad[TOP_RIGHT * 2 + 1] = top;
        quad[BOTTOM_RIGHT * 2] = right;
        quad[BOTTOM_RIGHT * 2 + 1] = bottom;
        quad[BOTTOM_LEFT * 2] = left;
        quad[BOTTOM_LEFT * 2 + 1] = bottom;
    }

    /**
     * Größe des entzerrten Bildes: Breite aus dem Mittel der beiden waagerechten Kanten, Höhe aus dem Mittel
     * der senkrechten. So bleibt das Seitenverhältnis der eingefassten Rechnung ungefähr erhalten.
     */
    public static int[] outputSize(float[] quad) {
        float top = dist(quad, TOP_LEFT, TOP_RIGHT);
        float bottom = dist(quad, BOTTOM_LEFT, BOTTOM_RIGHT);
        float left = dist(quad, TOP_LEFT, BOTTOM_LEFT);
        float right = dist(quad, TOP_RIGHT, BOTTOM_RIGHT);
        int w = Math.max(1, Math.round((top + bottom) / 2f));
        int h = Math.max(1, Math.round((left + right) / 2f));
        return new int[]{w, h};
    }

    /**
     * Werte für eine {@link ColorMatrix}: {@code brightness} und {@code contrast} laufen jeweils von −100 bis
     * +100, 0 lässt das Bild unverändert. Der Kontrast wird auf den Faktor 0,5…2,0 abgebildet; der Summand
     * {@code 128*(1-c)} hält Mittelgrau fest, sodass das Bild beim Anziehen des Kontrasts nicht wegläuft.
     */
    public static float[] colorMatrix(float brightness, float contrast) {
        float c = contrastFactor(contrast);
        float b = clamp(brightness, -100, 100) + 128f * (1f - c);
        return new float[]{
                c, 0, 0, 0, b,
                0, c, 0, 0, b,
                0, 0, c, 0, b,
                0, 0, 0, 1, 0};
    }

    /** −100 → 0,5 (flau), 0 → 1,0 (unverändert), +100 → 2,0 (knackig). */
    private static float contrastFactor(float contrast) {
        float v = clamp(contrast, -100, 100);
        return v < 0 ? 1f + v / 200f : 1f + v / 100f;
    }

    /**
     * Wendet Zuschnitt bzw. Entzerrung und die Tonwertkorrektur in einem Zeichendurchgang an und liefert das
     * Ergebnis als neues Bitmap. {@code src} bleibt unangetastet.
     */
    public static Bitmap apply(Bitmap src, float[] quad, boolean rectMode,
                               float brightness, float contrast) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(colorMatrix(brightness, contrast))));
        if (rectMode) {
            int left = Math.round(Math.min(quad[TOP_LEFT * 2], quad[BOTTOM_LEFT * 2]));
            int top = Math.round(Math.min(quad[TOP_LEFT * 2 + 1], quad[TOP_RIGHT * 2 + 1]));
            int right = Math.round(Math.max(quad[TOP_RIGHT * 2], quad[BOTTOM_RIGHT * 2]));
            int bottom = Math.round(Math.max(quad[BOTTOM_LEFT * 2 + 1], quad[BOTTOM_RIGHT * 2 + 1]));
            left = (int) clamp(left, 0, src.getWidth() - 1);
            top = (int) clamp(top, 0, src.getHeight() - 1);
            right = (int) clamp(right, left + 1, src.getWidth());
            bottom = (int) clamp(bottom, top + 1, src.getHeight());
            Bitmap out = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            canvas.translate(-left, -top);
            canvas.drawBitmap(src, 0, 0, paint);
            return out;
        }
        int[] size = outputSize(quad);
        float[] dst = {0, 0, size[0], 0, size[0], size[1], 0, size[1]};
        Matrix m = new Matrix();
        if (!m.setPolyToPoly(quad, 0, dst, 0, 4)) {
            // Entartetes Viereck (drei Punkte auf einer Linie) – dann lieber das Bild unverzerrt lassen.
            m.reset();
        }
        Bitmap out = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, m, paint);
        return out;
    }

    /**
     * Welche Datei der Editor als Vorlage lädt: die Sicherung nur, wenn der Nutzer ausdrücklich wieder beim
     * Original beginnen will <b>und</b> es sie gibt – sonst die aktuelle (evtl. schon bearbeitete) Datei.
     */
    public static String sourceFor(boolean fromBackup, String target, String backup, boolean backupExists) {
        return fromBackup && backupExists && backup != null && !backup.isEmpty() ? backup : target;
    }

    /**
     * Dekodiert ein JPEG unterabgetastet, sodass die lange Kante höchstens {@code maxEdge} misst – für
     * Vorschau und Betrachter, damit große Aufnahmen den Speicher nicht sprengen. {@code null} bei Fehler.
     */
    public static Bitmap decode(java.io.File file, int maxEdge) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longEdge / (sample * 2) >= maxEdge) {
                sample *= 2;
            }
            android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
            opt.inSampleSize = sample;
            return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
        } catch (Exception e) {
            return null;
        }
    }

    private static float dist(float[] quad, int a, int b) {
        float dx = quad[a * 2] - quad[b * 2];
        float dy = quad[a * 2 + 1] - quad[b * 2 + 1];
        return (float) Math.hypot(dx, dy);
    }

    /** Hält {@code v} mindestens {@link #MIN_SIDE} von {@code fixed} entfernt, ohne aus [0, max] zu laufen. */
    private static float keepAway(float v, float fixed, float max) {
        if (Math.abs(v - fixed) >= MIN_SIDE) {
            return v;
        }
        float below = fixed - MIN_SIDE;
        float above = fixed + MIN_SIDE;
        if (v <= fixed) {
            return below >= 0 ? below : Math.min(above, max);
        }
        return above <= max ? above : Math.max(below, 0);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
