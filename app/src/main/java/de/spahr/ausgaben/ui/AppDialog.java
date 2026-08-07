package de.spahr.ausgaben.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.ButtonBarLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;

import de.spahr.ausgaben.R;

/**
 * Jeder Dialog der App. Bündelt, was alle gemeinsam haben: den Theme-Aufsatz mit 16dp-Radius und
 * grünem Kopfband, dazu den grünen Rahmen um das ganze Fenster.
 *
 * <p>Der Rahmen entsteht nicht über eine eigene Zeichnung, sondern am Hintergrund, den
 * {@link MaterialAlertDialogBuilder} ohnehin anlegt: der behält seine Form und seine Ränder, und es
 * kommt nur der Strich dazu. So bleibt der Dialog in jeder Bildschirmgröße dort, wo Material ihn
 * hinstellt.</p>
 *
 * <p>Zerstörende Abfragen (löschen, zurücksetzen) über {@link #destructive(Context)} – dort ist die
 * bestätigende Taste rot statt grün.</p>
 */
public class AppDialog extends MaterialAlertDialogBuilder {

    /** Stärke des Rahmens in dp. */
    private static final float STROKE_DP = 2f;

    public AppDialog(@NonNull Context context) {
        super(context, R.style.ThemeOverlay_Ausgaben_Dialog);
    }

    private AppDialog(@NonNull Context context, int themeOverlay) {
        super(context, themeOverlay);
    }

    /** Abfrage, die etwas unwiderruflich entfernt: bestätigende Taste rot. */
    public static AppDialog destructive(@NonNull Context context) {
        return new AppDialog(context, R.style.ThemeOverlay_Ausgaben_Dialog_Destructive);
    }

    /** Überschrift; sie wandert in das eigene Kopfband statt in den Material-Titel. */
    private CharSequence title;

    @NonNull
    @Override
    public AppDialog setTitle(CharSequence text) {
        this.title = text;
        return this;
    }

    @NonNull
    @Override
    public AppDialog setTitle(int textId) {
        return setTitle(getContext().getText(textId));
    }

    @NonNull
    @Override
    public AlertDialog create() {
        View band = null;
        if (title != null) {
            band = LayoutInflater.from(getContext()).inflate(R.layout.dialog_title, null, false);
            ((TextView) band.findViewById(R.id.dialogTitle)).setText(title);
            setCustomTitle(band);
        }
        AlertDialog dialog = super.create();
        frame(dialog);
        if (band != null) {
            band.findViewById(R.id.dialogClose).setOnClickListener(v -> dialog.cancel());
        }
        keepButtonsInOneRow(dialog);
        return dialog;
    }

    /**
     * Die Tastenzeile darf nicht umbrechen. Material legt sie in eine {@code ScrollView}: gestapelte
     * Tasten wachsen dort nicht nach unten heraus, sondern verschwinden im Bildlauf – man sieht nur
     * noch die oberste. Nebeneinander ist auch das Gewohnte.
     */
    private static void keepButtonsInOneRow(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            unstack(window.getDecorView());
        }
    }

    private static void unstack(View view) {
        if (view instanceof ButtonBarLayout) {
            ((ButtonBarLayout) view).setAllowStacking(false);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                unstack(group.getChildAt(i));
            }
        }
    }

    /**
     * Legt den grünen Strich um das Dialogfenster. Der Hintergrund ist eine {@link InsetDrawable} um
     * eine {@link MaterialShapeDrawable}; passt das nicht (etwa nach einem Bibliotheks-Wechsel),
     * bleibt der Dialog eben ohne Rahmen – kein Grund, ihn gar nicht zu zeigen.
     */
    private static void frame(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        Drawable background = window.getDecorView().getBackground();
        if (background instanceof InsetDrawable) {
            background = ((InsetDrawable) background).getDrawable();
        }
        if (!(background instanceof MaterialShapeDrawable)) {
            return;
        }
        Context context = dialog.getContext();
        float stroke = STROKE_DP * context.getResources().getDisplayMetrics().density;
        ((MaterialShapeDrawable) background).setStroke(
                stroke, ContextCompat.getColor(context, R.color.green_primary));
    }
}
