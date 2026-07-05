package de.spahr.ausgaben.wear;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Wie am Phone: liefert {@link Resources}, die Texte aus {@link WearStrings} der aktiven Sprache
 * zurückgeben. Damit werden {@code @string/…} in Layouts und {@code getString(R.string.…)} automatisch
 * übersetzt; fehlt ein Schlüssel, greift die gebündelte Ressource.
 */
public class WearLocaleWrapper extends ContextWrapper {

    private Resources wrapped;

    private WearLocaleWrapper(Context base) {
        super(base);
    }

    public static ContextWrapper wrap(Context base) {
        WearStrings.ensureLoaded(base);
        return new WearLocaleWrapper(base);
    }

    @Override
    public Resources getResources() {
        if (wrapped == null) {
            wrapped = new TranslatedResources(super.getResources());
        }
        return wrapped;
    }

    private static final class TranslatedResources extends Resources {

        private static final ConcurrentHashMap<Integer, String> NAME_CACHE = new ConcurrentHashMap<>();

        @SuppressWarnings("deprecation")
        TranslatedResources(Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        }

        @Override
        public CharSequence getText(int id) throws NotFoundException {
            String v = lookup(id);
            return v != null ? v : super.getText(id);
        }

        @Override
        public String getString(int id) throws NotFoundException {
            String v = lookup(id);
            return v != null ? v : super.getString(id);
        }

        @Override
        public String getString(int id, Object... formatArgs) throws NotFoundException {
            String v = lookup(id);
            if (v != null) {
                return String.format(WearStrings.locale(), v, formatArgs);
            }
            return super.getString(id, formatArgs);
        }

        private String lookup(int id) {
            String name = NAME_CACHE.get(id);
            if (name == null) {
                try {
                    name = getResourceEntryName(id);
                } catch (NotFoundException e) {
                    return null;
                }
                NAME_CACHE.put(id, name);
            }
            return WearStrings.get(name);
        }
    }
}
