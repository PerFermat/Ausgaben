package de.spahr.ausgaben.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import de.spahr.ausgaben.R;

/**
 * Wendet die Akzentfarbe des aktiven Profils zur Laufzeit an, ohne die Activity neu aufzubauen:
 * Toolbar-Hintergrund samt Titel/Icons, Statusleiste und alle Elemente, die heute fest
 * {@code @color/green_primary}, {@code @color/green_dark} oder {@code @color/button_accent} tragen –
 * Buttons (Füllung, Rahmen, Schrift), {@link FloatingActionButton}s, einfache Views mit grüner
 * Hintergrundfläche (z. B. der Schubladenkopf), {@link TextView}s mit grüner Schrift/Symbolfarbe sowie
 * {@link MaterialSwitch} und {@link Slider} (deren „an"/aktiver Zustand). Dialoge laufen in einem
 * eigenen Fenster und werden separat über {@link #applyToDialog} eingefärbt (siehe
 * {@link de.spahr.ausgaben.ui.AppDialog}) – inklusive eines eigenen Inhalts-Views, falls vorhanden.
 *
 * <p>Ist die gewählte Farbe hell, wechseln Titel/Icons auf der Toolbar automatisch auf Schwarz statt
 * Weiß – sonst verschwinden sie auf hellem Grund (siehe {@link #contrastColor}).</p>
 */
public final class AccentColor {

    private AccentColor() {
    }

    /** Akzentfarbe des aktiven Profils. */
    public static int current(Context context) {
        ProfileManager.Profile active = new ProfileManager(context).getActiveProfile();
        return active != null ? active.accentColor : ProfileManager.DEFAULT_ACCENT_COLOR;
    }

    /** Kontrastfarbe (Schwarz auf hellem, Weiß auf dunklem Grund) für Text/Icons auf {@code color}. */
    public static int contrastColor(int color) {
        return isLight(color) ? Color.BLACK : Color.WHITE;
    }

    private static boolean isLight(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.6;
    }

    /** Abgedunkelte Variante für „dunkle" Elemente (Statusleiste, CalcKey.Ok). */
    private static int darkVariant(int color) {
        return ColorUtils.blendARGB(color, Color.BLACK, 0.25f);
    }

    /** Färbt Toolbar (Hintergrund, Titel, Icons), Statusleiste und den ganzen Inhalt der Activity ein. */
    public static void apply(Activity activity) {
        int color = current(activity);
        int contrast = contrastColor(color);
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(color);
            toolbar.setTitleTextColor(contrast);
            toolbar.setSubtitleTextColor(contrast);
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().mutate().setTint(contrast);
            }
            if (toolbar.getOverflowIcon() != null) {
                toolbar.getOverflowIcon().mutate().setTint(contrast);
            }
            Menu menu = toolbar.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                Drawable icon = item.getIcon();
                if (icon != null) {
                    icon.mutate().setTint(contrast);
                }
            }
        }
        if (activity.getWindow() != null) {
            activity.getWindow().setStatusBarColor(darkVariant(color));
        }
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            retint(activity, content, color, contrast);
        }
    }

    /**
     * Färbt die Tasten eines {@link de.spahr.ausgaben.ui.AppDialog} ein – Dialoge laufen in einem
     * eigenen Fenster, {@link #apply} erreicht sie nicht. {@code tintPositiveBackground} = false bei
     * zerstörenden Abfragen: deren bestätigende Taste bleibt bewusst rot als Warnsignal.
     *
     * @param customView der über {@code setView(...)} gesetzte Inhalt des Dialogs (z. B. eine eigene
     *                    Rechentastatur), oder {@code null}; wird wie eine Activity-Ansicht eingefärbt.
     */
    public static void applyToDialog(AlertDialog dialog, boolean tintPositiveBackground, View customView) {
        Context context = dialog.getContext();
        int color = current(context);
        int contrast = contrastColor(color);
        if (customView != null) {
            retint(context, customView, color, contrast);
        }
        dialog.setOnShowListener(d -> {
            if (tintPositiveBackground) {
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive instanceof MaterialButton) {
                    ((MaterialButton) positive).setBackgroundTintList(ColorStateList.valueOf(color));
                    positive.setTextColor(contrast);
                }
            }
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(color);
            }
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) {
                neutral.setTextColor(color);
            }
        });
    }

    /** Wie {@link #applyToDialog(AlertDialog, boolean, View)}, ohne eigenen Inhalts-View. */
    public static void applyToDialog(AlertDialog dialog, boolean tintPositiveBackground) {
        applyToDialog(dialog, tintPositiveBackground, null);
    }

    /**
     * Läuft rekursiv über den View-Baum und färbt alles um, was noch die Standardfarbe trägt: Buttons
     * (Füllung/Rahmen/Schrift), {@link FloatingActionButton}s, einfache Views mit grüner
     * Hintergrundfarbe, {@link TextView}s mit grüner Schrift/Symbolfarbe sowie Switch/Slider.
     */
    private static void retint(Context context, View view, int color, int contrast) {
        int greenPrimary = ProfileManager.DEFAULT_ACCENT_COLOR;
        int greenDark = ContextCompat.getColor(context, R.color.green_dark);
        int buttonAccent = ContextCompat.getColor(context, R.color.button_accent);

        if (view instanceof MaterialSwitch) {
            retintSwitch((MaterialSwitch) view, color);
        } else if (view instanceof Slider) {
            retintSlider((Slider) view, color);
        } else if (view instanceof MaterialButton) {
            retintMaterialButton((MaterialButton) view, color, contrast, greenPrimary, greenDark, buttonAccent);
        } else if (view instanceof FloatingActionButton) {
            retintFab((FloatingActionButton) view, color, contrast, greenPrimary);
        } else if (view instanceof TextView) {
            retintTextView((TextView) view, color, buttonAccent);
        }

        // Einfache Views (kein Button/Switch/…) mit reiner grüner Füllfarbe – z. B. der Schubladenkopf.
        if (!(view instanceof MaterialButton) && !(view instanceof FloatingActionButton)) {
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable && ((ColorDrawable) bg).getColor() == greenPrimary) {
                view.setBackgroundColor(color);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                retint(context, group.getChildAt(i), color, contrast);
            }
        }
    }

    private static void retintMaterialButton(MaterialButton button, int color, int contrast,
                                              int greenPrimary, int greenDark, int buttonAccent) {
        ColorStateList bgTint = button.getBackgroundTintList();
        if (bgTint != null) {
            int def = bgTint.getDefaultColor();
            if (def == greenPrimary) {
                button.setBackgroundTintList(ColorStateList.valueOf(color));
                button.setTextColor(contrast);
            } else if (def == greenDark) {
                int dark = darkVariant(color);
                button.setBackgroundTintList(ColorStateList.valueOf(dark));
                button.setTextColor(contrastColor(dark));
            }
        }
        ColorStateList strokeColor = button.getStrokeColor();
        if (strokeColor != null && strokeColor.getDefaultColor() == buttonAccent) {
            button.setStrokeColor(ColorStateList.valueOf(color));
        }
        ColorStateList textColors = button.getTextColors();
        if (textColors != null && textColors.getDefaultColor() == buttonAccent) {
            button.setTextColor(color);
        }
    }

    /**
     * {@link FloatingActionButton} ist kein {@link MaterialButton} (eigene Klassenhierarchie) und wird
     * von dessen Behandlung nicht erfasst.
     */
    private static void retintFab(FloatingActionButton fab, int color, int contrast, int greenPrimary) {
        ColorStateList bgTint = fab.getBackgroundTintList();
        if (bgTint != null && bgTint.getDefaultColor() == greenPrimary) {
            fab.setBackgroundTintList(ColorStateList.valueOf(color));
            fab.setSupportImageTintList(ColorStateList.valueOf(contrast));
        }
    }

    /** TextViews (nicht Buttons) mit grüner Schrift- bzw. Symbolfarbe, z. B. „Konto hinzufügen". */
    private static void retintTextView(TextView tv, int color, int buttonAccent) {
        if (tv.getCurrentTextColor() == buttonAccent) {
            tv.setTextColor(color);
        }
        ColorStateList drawableTint = TextViewCompat.getCompoundDrawableTintList(tv);
        if (drawableTint != null && drawableTint.getDefaultColor() == buttonAccent) {
            TextViewCompat.setCompoundDrawableTintList(tv, ColorStateList.valueOf(color));
        }
    }

    /**
     * Nur die Spur bekommt im „an"-Zustand die Profilfarbe; die „aus"-Farbe (grau, hell-/
     * dunkelmodusabhängig) bleibt wie vom Material3-Theme vorgegeben. Der Schalterkreis bleibt im
     * „an"-Zustand kontrastreich (weiß auf dunklen/kräftigen Farben) statt in derselben Farbe wie die
     * Spur zu verschwinden.
     */
    private static void retintSwitch(MaterialSwitch sw, int color) {
        sw.setTrackTintList(withCheckedColor(sw.getTrackTintList(), color));
        sw.setThumbTintList(withCheckedColor(sw.getThumbTintList(), contrastColor(color)));
    }

    private static ColorStateList withCheckedColor(ColorStateList original, int color) {
        int fallback = original != null ? original.getDefaultColor() : Color.GRAY;
        int uncheckedColor = original != null
                ? original.getColorForState(new int[]{-android.R.attr.state_checked}, fallback)
                : fallback;
        return new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{color, uncheckedColor});
    }

    /** Aktiver Teil des Schiebereglers (Spur, Griff, Halo) in Profilfarbe; der inaktive Teil bleibt grau. */
    private static void retintSlider(Slider slider, int color) {
        ColorStateList csl = ColorStateList.valueOf(color);
        slider.setThumbTintList(csl);
        slider.setTrackActiveTintList(csl);
        slider.setHaloTintList(csl);
    }
}
