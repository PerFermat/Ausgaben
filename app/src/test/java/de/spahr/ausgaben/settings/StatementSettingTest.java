package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Das Einlesen von Bankabrechnungen ist eine Einstellung, und ihr Standardwert ist die eigentliche
 * Aussage: <b>aus</b>.
 *
 * <p>Die Erkennung trägt nicht bei jeder Bank. Wäre sie ab Werk an, träfe der Nutzer sie, ohne je den
 * Hinweis gelesen zu haben, an wie vielen Banken sie gemessen wurde — und übernähme im ungünstigen Fall
 * falsche Zahlen. Dieser Test hält den Standardwert fest, damit er nicht beiläufig kippt.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementSettingTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    @Test
    public void abWerkAusgeschaltet() {
        assertFalse(new SettingsStore(ctx).isStatementEnabled());
    }

    @Test
    public void einschaltenBleibtErhalten() {
        new SettingsStore(ctx).setStatementEnabled(true);

        // Eine neue Instanz, wie sie jede Seite der App für sich anlegt.
        assertTrue(new SettingsStore(ctx).isStatementEnabled());
    }

    @Test
    public void wiederAusschaltenGeht() {
        SettingsStore settings = new SettingsStore(ctx);
        settings.setStatementEnabled(true);
        settings.setStatementEnabled(false);

        assertFalse(new SettingsStore(ctx).isStatementEnabled());
    }
}
