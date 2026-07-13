package de.spahr.ausgaben.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Schmaler Verlaufsbalken, dessen hellere Stelle langsam von links nach rechts und wieder zurück wandert
 * – als „es wird gearbeitet"-Anzeige (z. B. im Import-Banner). Zwei Gelbtöne per {@link #setColors}.
 */
public class ShimmerView extends View {

    private final Paint paint = new Paint();
    private int baseColor = 0xFFFFF59D;
    private int highlightColor = 0xFFFFEB3B;
    private float phase = 0f;   // 0 = ganz links, 1 = ganz rechts
    private ValueAnimator animator;

    public ShimmerView(Context context) {
        super(context);
    }

    public ShimmerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setColors(int base, int highlight) {
        this.baseColor = base;
        this.highlightColor = highlight;
        invalidate();
    }

    /** Startet die hin- und herwandernde Animation. */
    public void start() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float cx = phase * w;                 // Mitte der hellen Stelle
        float band = w * 0.6f;                // Breite des Übergangs
        LinearGradient g = new LinearGradient(cx - band, 0, cx + band, 0,
                new int[]{baseColor, highlightColor, baseColor},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        canvas.drawRect(0, 0, w, h, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
