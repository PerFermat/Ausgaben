package de.spahr.ausgaben.wear;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.wear.tiles.ActionBuilders;
import androidx.wear.tiles.ColorBuilders;
import androidx.wear.tiles.DimensionBuilders;
import androidx.wear.tiles.LayoutElementBuilders;
import androidx.wear.tiles.ModifiersBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.ResourceBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import androidx.wear.tiles.TimelineBuilders;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Wear-Tile („Widget"): zeigt „Buchung erfassen" und die drei Typ-Knöpfe (Einnahme grün, Umbuchung gelb,
 * Ausgabe rot). Ein Tipp auf einen Knopf öffnet die App und startet direkt die Spracheingabe für diesen
 * Typ. Bewusst minimal (Tiles-1.1-Layout-Builder, kein Compose, keine Bild-Ressourcen).
 */
public class ExpenseTileService extends TileService {

    private static final String RESOURCES_VERSION = "1";

    private static final int GREEN = 0xFF2E7D32;
    private static final int YELLOW = 0xFFF9A825;
    private static final int RED = 0xFFC62828;
    private static final int WHITE = 0xFFFFFFFF;

    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            RequestBuilders.TileRequest requestParams) {

        LayoutElementBuilders.Row buttons = new LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(typeButton(WearPaths.TYPE_INCOME, GREEN, "+"))
                .addContent(spacerW())
                .addContent(typeButton(WearPaths.TYPE_TRANSFER, YELLOW, "↔"))
                .addContent(spacerW())
                .addContent(typeButton(WearPaths.TYPE_EXPENSE, RED, "−"))
                .build();

        LayoutElementBuilders.Column content = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(tr("wear_title", R.string.wear_title))
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setColor(ColorBuilders.argb(WHITE))
                                .setSize(DimensionBuilders.sp(15))
                                .build())
                        .build())
                .addContent(new LayoutElementBuilders.Spacer.Builder()
                        .setHeight(DimensionBuilders.dp(10))
                        .build())
                .addContent(buttons)
                .build();

        // Auf dem Rundschirm den Inhalt vertikal + horizontal zentrieren (sonst wird der Titel oben
        // vom runden Rand beschnitten).
        LayoutElementBuilders.Box root = new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(content)
                .build();

        TileBuilders.Tile tile = new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTimeline(new TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(new LayoutElementBuilders.Layout.Builder()
                                        .setRoot(root)
                                        .build())
                                .build())
                        .build())
                .build();

        return immediate(tile);
    }

    /** Ein farbiger, runder Knopf mit Symbol, der die App für {@code type} startet. */
    private LayoutElementBuilders.LayoutElement typeButton(String type, int color, String symbol) {
        ActionBuilders.LaunchAction launch = new ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(getPackageName())
                        .setClassName(WearMainActivity.class.getName())
                        .addKeyToExtraMapping(WearMainActivity.EXTRA_TYPE,
                                new ActionBuilders.AndroidStringExtra.Builder().setValue(type).build())
                        .build())
                .build();

        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(48))
                .setHeight(DimensionBuilders.dp(48))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setBackground(new ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(color))
                                .setCorner(new ModifiersBuilders.Corner.Builder()
                                        .setRadius(DimensionBuilders.dp(24))
                                        .build())
                                .build())
                        .setClickable(new ModifiersBuilders.Clickable.Builder()
                                .setId(type)
                                .setOnClick(launch)
                                .build())
                        .build())
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(symbol)
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setColor(ColorBuilders.argb(WHITE))
                                .setSize(DimensionBuilders.sp(22))
                                .build())
                        .build())
                .build();
    }

    /** Übersetzter Text der aktiven Sprache (vom Phone) oder gebündelte Ressource als Fallback. */
    private String tr(String key, int resId) {
        WearStrings.ensureLoaded(this);
        String v = WearStrings.get(key);
        return v != null ? v : getString(resId);
    }

    private LayoutElementBuilders.Spacer spacerW() {
        return new LayoutElementBuilders.Spacer.Builder()
                .setWidth(DimensionBuilders.dp(8))
                .build();
    }

    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onResourcesRequest(
            RequestBuilders.ResourcesRequest requestParams) {
        return immediate(new ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build());
    }

    private static <T> ListenableFuture<T> immediate(T value) {
        ResolvableFuture<T> future = ResolvableFuture.create();
        future.set(value);
        return future;
    }
}
