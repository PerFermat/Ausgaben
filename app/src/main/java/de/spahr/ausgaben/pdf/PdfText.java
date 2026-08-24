package de.spahr.ausgaben.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Der Text eines PDF als Zeilen mit Wortpositionen — die Grundlage, auf der die Auslese einer
 * Depotabrechnung arbeitet.
 *
 * <p>Die Klasse ist <b>Android-frei</b>: sie kennt weder PDF noch Context. Gefüttert wird sie über
 * {@link Builder} — im Betrieb von {@link PdfTextExtractor}, im Test von Hand. Damit lässt sich alles,
 * was auf dem Text aufsetzt, ohne eine einzige PDF-Datei prüfen.</p>
 *
 * <p>Eine Zeile entsteht nicht aus dem PDF (dort gibt es keine Zeilen, nur Textstücke an Koordinaten),
 * sondern aus der y-Position: Wörter, die dicht genug beieinander liegen, gehören zusammen.</p>
 */
public final class PdfText {

    /**
     * Wie weit zwei Wörter senkrecht auseinanderliegen dürfen und trotzdem als eine Zeile gelten.
     * Drei Punkte sind knapp unter einer Zeilenhöhe bei üblichen 8–12-Punkt-Schriften und großzügig
     * genug für Wörter, die minimal versetzt gesetzt sind.
     */
    private static final float LINE_TOLERANCE = 3f;

    /** Weniger Wörter als das heißt: kein Text im Dokument, nur ein Bild. */
    private static final int MIN_WORDS = 5;

    /** Ein Wort mit seiner Lage auf der Seite. */
    public static final class Word {
        public final String text;
        /** Linke Kante in PDF-Punkten, von links gemessen. */
        public final float x;
        /** Rechte Kante — nötig, um „steht rechts daneben" zu beurteilen. */
        public final float endX;
        public final float y;

        public Word(String text, float x, float endX, float y) {
            this.text = text;
            this.x = x;
            this.endX = endX;
            this.y = y;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Eine Zeile: die Wörter einer y-Position, von links nach rechts. */
    public static final class Line {
        public final int page;
        public final float y;
        public final List<Word> words;

        Line(int page, float y, List<Word> words) {
            this.page = page;
            this.y = y;
            this.words = Collections.unmodifiableList(words);
        }

        /** Die Zeile als Text, Wörter durch ein einfaches Leerzeichen getrennt. */
        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Word w : words) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(w.text);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return text();
        }
    }

    private final List<Line> lines;
    private final int pageCount;
    private final int wordCount;

    private PdfText(List<Line> lines, int pageCount, int wordCount) {
        this.lines = Collections.unmodifiableList(lines);
        this.pageCount = pageCount;
        this.wordCount = wordCount;
    }

    /** Alle Zeilen in Lesereihenfolge: Seite für Seite, innerhalb der Seite von oben nach unten. */
    public List<Line> lines() {
        return lines;
    }

    public int pageCount() {
        return pageCount;
    }

    /**
     * Ob das Dokument überhaupt Text enthält. {@code false} heißt in aller Regel: eine eingescannte
     * Seite ohne Textebene. Daraus ist ohne Texterkennung nichts zu holen, und die App muss das sagen,
     * statt kommentarlos nichts zu finden.
     */
    public boolean hasText() {
        return wordCount >= MIN_WORDS;
    }

    /** Das ganze Dokument als Text, eine Zeile je Zeile. */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            sb.append(l.text()).append('\n');
        }
        return sb.toString();
    }

    /** Sammelt Wörter und bündelt sie beim {@link #build()} zu Zeilen. */
    public static final class Builder {

        private final List<Word> words = new ArrayList<>();
        private final List<Integer> pages = new ArrayList<>();
        private int maxPage = -1;

        /**
         * Nimmt ein Wort auf. {@code y} wird von <b>oben</b> gemessen (PdfBox liefert es so), damit
         * größeres y weiter unten bedeutet und die Lesereihenfolge einfach aufsteigend ist.
         */
        public Builder add(int page, String text, float x, float endX, float y) {
            String t = text == null ? "" : text.trim();
            if (t.isEmpty()) {
                return this;
            }
            words.add(new Word(t, x, endX, y));
            pages.add(page);
            maxPage = Math.max(maxPage, page);
            return this;
        }

        public PdfText build() {
            // Nach Seite, dann y, dann x — das ist die Lesereihenfolge.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < words.size(); i++) {
                order.add(i);
            }
            Collections.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    int p = Integer.compare(pages.get(a), pages.get(b));
                    if (p != 0) {
                        return p;
                    }
                    int yc = Float.compare(words.get(a).y, words.get(b).y);
                    return yc != 0 ? yc : Float.compare(words.get(a).x, words.get(b).x);
                }
            });

            List<Line> result = new ArrayList<>();
            List<Word> current = new ArrayList<>();
            int currentPage = -1;
            float currentY = 0;
            for (int idx : order) {
                Word w = words.get(idx);
                int page = pages.get(idx);
                boolean sameLine = !current.isEmpty() && page == currentPage
                        && Math.abs(w.y - currentY) <= LINE_TOLERANCE;
                if (!sameLine && !current.isEmpty()) {
                    result.add(new Line(currentPage, currentY, sortedByX(current)));
                    current = new ArrayList<>();
                }
                if (current.isEmpty()) {
                    currentPage = page;
                    currentY = w.y;
                }
                current.add(w);
            }
            if (!current.isEmpty()) {
                result.add(new Line(currentPage, currentY, sortedByX(current)));
            }
            return new PdfText(result, maxPage + 1, words.size());
        }

        private static List<Word> sortedByX(List<Word> line) {
            List<Word> copy = new ArrayList<>(line);
            Collections.sort(copy, new Comparator<Word>() {
                @Override
                public int compare(Word a, Word b) {
                    return Float.compare(a.x, b.x);
                }
            });
            return copy;
        }
    }
}
