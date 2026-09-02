package de.spahr.ausgaben.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Ein Dialog, der eine Bildschirmdrehung übersteht.
 *
 * <p>Ein {@code new AppDialog(activity).show()} hängt am Fenster der Activity. Wird die gedreht, ist
 * der Dialog weg — mit ihm die Antwort, auf die der Ablauf dahinter wartet. Meist ist das nur
 * ärgerlich; in der Wertpapier-Maske war es ein Datenfehler: Die Bewegung war zu dem Zeitpunkt schon
 * gebucht, und der Dialog trug den einzigen Weg zum {@code finish()}. Nach der Drehung stand die
 * ausgefüllte Maske wieder da, der Speichern-Knopf war wieder frei — und ein zweiter Tipp legte alles
 * ein zweites Mal an.</p>
 *
 * <p>Ein {@link DialogFragment} baut das Fenstersystem dagegen selbst wieder auf. Es darf dafür aber
 * nichts festhalten, was die Drehung nicht übersteht — keine Rückrufe, keine Verweise auf die alte
 * Activity. Deshalb kennt diese Klasse den Dialog gar nicht: Sie merkt sich nur einen <b>Schlüssel</b>
 * und ein paar leichte Angaben und lässt die Maske ihn jedes Mal neu bauen. Nach der Drehung ist das
 * die <b>neue</b> Maske, und die Knöpfe wirken auf deren Felder.</p>
 *
 * <p>Was so nicht geht: ein Dialog, dessen Inhalt an etwas hängt, das nur im Speicher der alten
 * Activity lag (ein gelesenes PDF, ein entschlüsseltes Archiv). Solche Angaben gehören nicht in ein
 * {@link Bundle} — dort ist bei einer knappen Megabyte-Grenze Schluss, und ein Archiv sprengt sie.
 * Die Maske muss sie in diesem Fall selbst wiederbeschaffen; siehe die Aufrufer.</p>
 */
public class HostedDialog extends DialogFragment {

    /** Die Maske, die ihre Dialoge selbst baut. */
    public interface Host {
        /**
         * Baut den Dialog zu diesem Schlüssel — beim ersten Mal und nach jeder Drehung erneut.
         *
         * @return der Dialog, oder {@code null}, wenn er sich nicht mehr bauen lässt (dann verschwindet
         *         er einfach; die Maske entscheidet selbst, was stattdessen zu tun ist)
         */
        Dialog buildDialog(String key, Bundle args);

        /** Der Dialog wurde weggetippt oder mit Zurück verlassen. */
        void onDialogCancelled(String key, Bundle args);
    }

    private static final String ARG_KEY = "hostedDialogKey";

    /** Zeigt den Dialog; ein bereits offener mit demselben Schlüssel bleibt stehen. */
    public static void show(FragmentActivity activity, String key, Bundle args) {
        if (activity.getSupportFragmentManager().findFragmentByTag(key) != null) {
            return;
        }
        Bundle all = args == null ? new Bundle() : new Bundle(args);
        all.putString(ARG_KEY, key);
        HostedDialog fragment = new HostedDialog();
        fragment.setArguments(all);
        // commitAllowingStateLoss: Der Dialog kommt oft aus einem Hintergrundfaden zurück, und dann
        // kann die Maske schon im Hintergrund stehen. Ein Absturz wäre hier die schlechtere Antwort
        // als ein Dialog, der in diesem seltenen Fall ausbleibt.
        activity.getSupportFragmentManager().beginTransaction()
                .add(fragment, key)
                .commitAllowingStateLoss();
    }

    /** Schließt den Dialog zu diesem Schlüssel, falls er offen ist. */
    public static void dismiss(FragmentActivity activity, String key) {
        androidx.fragment.app.Fragment f = activity.getSupportFragmentManager().findFragmentByTag(key);
        if (f instanceof HostedDialog) {
            ((HostedDialog) f).dismissAllowingStateLoss();
        }
    }

    /** Ob zu diesem Schlüssel gerade ein Dialog offen ist. */
    public static boolean isShowing(FragmentActivity activity, String key) {
        return activity.getSupportFragmentManager().findFragmentByTag(key) != null;
    }

    private String key() {
        Bundle args = getArguments();
        return args == null ? "" : args.getString(ARG_KEY, "");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = host() == null ? null : host().buildDialog(key(), getArguments());
        if (dialog != null) {
            return dialog;
        }
        // Kein Dialog zu bauen: als leerer, sofort geschlossener zurückgeben – onCreateDialog darf
        // nicht null liefern.
        setShowsDialog(false);
        dismissAllowingStateLoss();
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        if (host() != null) {
            host().onDialogCancelled(key(), getArguments());
        }
    }

    private Host host() {
        return getActivity() instanceof Host ? (Host) getActivity() : null;
    }
}
