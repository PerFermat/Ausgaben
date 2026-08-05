package de.spahr.ausgaben.receipt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests für den Rechenkern der Belegbearbeitung: Startrahmen, Ziehen der Ecken in beiden Modi, Zielgröße der
 * Entzerrung und die Werte der Tonwertkorrektur. Die {@code apply}-Methode braucht Android-Bitmaps und wird
 * hier nicht abgedeckt.
 */
public class ReceiptEditTest {

    private static final float EPS = 0.001f;

    @Test
    public void startQuad_insideImageAndAxisAligned() {
        float[] q = ReceiptEdit.startQuad(1000, 500);
        for (int i = 0; i < 4; i++) {
            assertTrue(q[i * 2] >= 0 && q[i * 2] <= 1000);
            assertTrue(q[i * 2 + 1] >= 0 && q[i * 2 + 1] <= 500);
        }
        assertEquals(q[1], q[3], EPS);   // obere Kante waagerecht
        assertEquals(q[5], q[7], EPS);   // untere Kante waagerecht
        assertEquals(q[0], q[6], EPS);   // linke Kante senkrecht
        assertEquals(q[2], q[4], EPS);   // rechte Kante senkrecht
    }

    @Test
    public void moveCorner_rectMode_keepsRectangle() {
        float[] q = {0, 0, 100, 0, 100, 100, 0, 100};
        ReceiptEdit.moveCorner(q, ReceiptEdit.TOP_LEFT, 20, 30, true, 100, 100);
        assertArrayEquals(new float[]{20, 30, 100, 30, 100, 100, 20, 100}, q, EPS);
    }

    @Test
    public void moveCorner_rectMode_clampsToImage() {
        float[] q = {0, 0, 100, 0, 100, 100, 0, 100};
        ReceiptEdit.moveCorner(q, ReceiptEdit.BOTTOM_RIGHT, 500, -20, true, 100, 100);
        // Rechts/unten auf die Bildgrenze geklemmt; die Ecke oben-links bleibt stehen, das Rechteck kippt.
        assertArrayEquals(new float[]{0, 0, 100, 0, 100, 16, 0, 16}, q, EPS);
    }

    @Test
    public void moveCorner_quadMode_leavesNeighboursAlone() {
        float[] q = {0, 0, 100, 0, 100, 100, 0, 100};
        ReceiptEdit.moveCorner(q, ReceiptEdit.TOP_LEFT, 20, 30, false, 100, 100);
        assertArrayEquals(new float[]{20, 30, 100, 0, 100, 100, 0, 100}, q, EPS);
    }

    @Test
    public void outputSize_averagesOppositeEdges() {
        // Trapez: obere Kante 80, untere 120 → Breite 100; Höhe beidseitig 50.
        float[] q = {10, 0, 90, 0, 110, 50, -10, 50};
        int[] size = ReceiptEdit.outputSize(q);
        assertEquals(100, size[0]);
        assertTrue(size[1] >= 50 && size[1] <= 55);
    }

    @Test
    public void outputSize_neverZero() {
        int[] size = ReceiptEdit.outputSize(new float[]{5, 5, 5, 5, 5, 5, 5, 5});
        assertEquals(1, size[0]);
        assertEquals(1, size[1]);
    }

    @Test
    public void colorMatrix_neutralAtZero() {
        float[] m = ReceiptEdit.colorMatrix(0, 0);
        assertArrayEquals(new float[]{
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 1, 0}, m, EPS);
    }

    @Test
    public void colorMatrix_brightnessIsAnOffset() {
        float[] m = ReceiptEdit.colorMatrix(40, 0);
        assertEquals(1f, m[0], EPS);
        assertEquals(40f, m[4], EPS);
        assertEquals(40f, m[9], EPS);
        assertEquals(40f, m[14], EPS);
    }

    @Test
    public void colorMatrix_contrastKeepsMidGrey() {
        float[] full = ReceiptEdit.colorMatrix(0, 100);
        assertEquals(2f, full[0], EPS);
        assertEquals(-128f, full[4], EPS);      // 128*(1-2) – Mittelgrau bleibt Mittelgrau
        float[] flat = ReceiptEdit.colorMatrix(0, -100);
        assertEquals(0.5f, flat[0], EPS);
        assertEquals(64f, flat[4], EPS);
    }

    @Test
    public void colorMatrix_clampsOutOfRange() {
        assertArrayEquals(ReceiptEdit.colorMatrix(100, 100), ReceiptEdit.colorMatrix(500, 500), EPS);
    }
}
