package de.spahr.ausgaben.ui;

import android.widget.EditText;

import de.spahr.ausgaben.settings.AmountExpression;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.util.Quantile;

/**
 * Koppelt einen {@link ZeroMarkSlider} mit zwei Betragsfeldern. Der Regler läuft nicht über die
 * Beträge, sondern über die <b>Ränge</b> der vorhandenen Werte (0–100 %): der rechte Daumen auf 90 %
 * schneidet die größten 10 % ab. Ein einzelner Ausreißer zieht den Weg damit nicht mehr auf.
 *
 * <p>Angezeigt werden weiter <b>Beträge</b> – an der Beschriftung des Daumens wie in den beiden
 * Feldern. Ein von Hand getippter Betrag gilt <b>genau so</b>, auch wenn er zwischen zwei vorhandenen
 * Werten liegt; der Daumen springt dann nur optisch an die passende Stelle. Dieselbe Trennung wie bei
 * {@link MonthRange} zwischen monatsgenauem Regler und taggenauem Feld.</p>
 */
final class AmountRange {

    /** Beschriftung eines Betrags (mit Währung im Buchungsfilter, ohne im Depot). */
    interface Labels {
        String format(long cents);
    }

    private final ZeroMarkSlider slider;
    private final EditText fromField;
    private final EditText toField;
    private final long[] sorted;
    private long fromCents;
    private long toCents;
    private boolean touched;
    private boolean syncing;

    static AmountRange attach(ZeroMarkSlider slider, EditText fromField, EditText toField,
                              long[] sortedCents, Long initFrom, Long initTo, Labels labels) {
        return new AmountRange(slider, fromField, toField, sortedCents, initFrom, initTo, labels);
    }

    private AmountRange(ZeroMarkSlider slider, EditText fromField, EditText toField,
                        long[] sortedCents, Long initFrom, Long initTo, Labels labels) {
        this.slider = slider;
        this.fromField = fromField;
        this.toField = toField;
        this.sorted = sortedCents;

        slider.setValueFrom(0f);
        slider.setValueTo(100f);
        slider.setStepSize(1f);
        slider.setZeroPercent(Quantile.percentOfZero(sortedCents));
        slider.setLabelFormatter(value -> labels.format(Quantile.valueAt(sorted, value)));

        fromCents = initFrom != null ? initFrom : Quantile.valueAt(sorted, 0);
        toCents = initTo != null ? initTo : Quantile.valueAt(sorted, 100);
        touched = initFrom != null || initTo != null;
        float f = initFrom != null ? Quantile.percentOf(sorted, initFrom) : 0f;
        float t = initTo != null ? Quantile.percentOf(sorted, initTo) : 100f;
        slider.setValues((float) Math.round(f), (float) Math.round(Math.max(f, t)));
        updateFields();

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (syncing) {
                return;
            }
            fromCents = Quantile.valueAt(sorted, s.getValues().get(0));
            toCents = Quantile.valueAt(sorted, s.getValues().get(1));
            touched = true;
            updateFields();
        });
        fromField.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitField(fromField, true);
            }
        });
        toField.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitField(toField, false);
            }
        });
    }

    /** Übernimmt einen getippten Betrag; ungültige Eingabe fällt auf die Anzeige zurück. */
    private void commitField(EditText field, boolean isFrom) {
        String s = field.getText() == null ? "" : field.getText().toString().trim();
        Long cents = AmountExpression.toCents(s);
        if (cents == null) {
            updateFields();
            return;
        }
        if (isFrom) {
            fromCents = cents;
        } else {
            toCents = cents;
        }
        touched = true;
        // Daumen an die passende Stelle – rein optisch, der getippte Betrag bleibt genau erhalten.
        syncing = true;
        int f = Math.round(Quantile.percentOf(sorted, fromCents));
        int t = Math.round(Quantile.percentOf(sorted, toCents));
        if (f <= t) {
            slider.setValues((float) f, (float) t);
        }
        syncing = false;
    }

    /** Untere Grenze in Cent (vorzeichenbehaftet). */
    long getFromCents() {
        return fromCents;
    }

    long getToCents() {
        return toCents;
    }

    /** Nichts eingeschränkt: beide Daumen unberührt am Anschlag und kein Betrag getippt. */
    boolean isFullRange() {
        return !touched
                || (fromCents <= Quantile.valueAt(sorted, 0) && toCents >= Quantile.valueAt(sorted, 100));
    }

    private void updateFields() {
        syncing = true;
        fromField.setText(MoneyFormat.plain(fromCents));
        toField.setText(MoneyFormat.plain(toCents));
        syncing = false;
    }
}
