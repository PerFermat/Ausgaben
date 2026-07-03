package de.spahr.ausgaben.wear;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.wear.tiles.ActionBuilders;
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
 * Wear-Tile („Widget"): zeigt „Ausgabe erfassen"; ein Tipp öffnet die App und startet direkt die
 * Spracheingabe. Bewusst minimal gehalten (Tiles-1.1-Layout-Builder, kein Compose).
 */
public class ExpenseTileService extends TileService {

    private static final String RESOURCES_VERSION = "1";

    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            RequestBuilders.TileRequest requestParams) {

        ActionBuilders.LaunchAction launch = new ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(getPackageName())
                        .setClassName(WearMainActivity.class.getName())
                        .addKeyToExtraMapping(WearMainActivity.EXTRA_START_VOICE,
                                new ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build())
                        .build())
                .build();

        LayoutElementBuilders.Box root = new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(new ModifiersBuilders.Clickable.Builder()
                                .setId("open")
                                .setOnClick(launch)
                                .build())
                        .build())
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(getString(R.string.wear_title))
                        .build())
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
