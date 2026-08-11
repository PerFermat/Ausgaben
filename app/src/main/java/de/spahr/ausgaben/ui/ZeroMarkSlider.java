package de.spahr.ausgaben.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.slider.RangeSlider;

/**
 * Ein {@link RangeSlider} mit einem feinen Strich dort, wo im Betragsfilter das Vorzeichen wechselt:
 *
 * <pre>
 *   −10 ·······|························ 20
 * </pre>
 *
 * <p>Links vom Strich stehen die Ausgaben, rechts die Einnahmen. Ohne Vorzeichenwechsel (nur Ausgaben,
 * nur Einnahmen, Depotwerte) bleibt er weg.</p>
 */
public class ZeroMarkSlider extends RangeSlider {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float zeroFraction = -1f;

    public ZeroMarkSlider(@NonNull Context context) {
        this(context, null);
    }

    public ZeroMarkSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.sliderStyle);
    }

    public ZeroMarkSlider(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setColor(com.google.android.material.color.MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface));
        paint.setAlpha(140);
        paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Stelle der Null auf dem Weg (0–100 wie die Werte des Reglers); {@code < 0} = keine Markierung. */
    public void setZeroPercent(float percent) {
        zeroFraction = percent < 0 ? -1f : Math.max(0f, Math.min(100f, percent)) / 100f;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (zeroFraction < 0) {
            return;
        }
        // getTrackSidePadding()/getTrackWidth() sind öffentlich – der Strich sitzt damit genau auf der Spur.
        float x = getTrackSidePadding() + zeroFraction * getTrackWidth();
        float middle = getHeight() / 2f;
        float half = Math.max(getTrackHeight(), 8 * getResources().getDisplayMetrics().density) / 2f;
        canvas.drawLine(x, middle - half, x, middle + half, paint);
    }
}
