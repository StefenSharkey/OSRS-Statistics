package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class StatisticsKillOverlay extends Overlay {

    private final Client client;
    private final StatisticsPlugin plugin;
    private final StatisticsConfig config;
    private final TooltipManager tooltipManager;
    private final Database database;

    private Map<WorldPoint, HashMap<Integer, Integer>> killCountMap;
    private LocalDateTime lastUpdated;

    @Inject
    StatisticsKillOverlay(Client client, StatisticsPlugin plugin, StatisticsConfig config, TooltipManager tooltipManager) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;
        database = new Database(config);
        updateMaps();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.isKillOverlayEnabled()) {
            updateMaps();

            Player player = client.getLocalPlayer();

            if (player != null) {
                renderTiles(graphics);

                if (config.isKillTooltipEnabled()) {
                    renderTooltip();
                }
            }

        }

        return null;
    }

    private void renderTooltip() {
        Tile selectedTile = client.getSelectedSceneTile();

        if (selectedTile != null) {
            WorldPoint worldPoint = selectedTile.getWorldLocation();

            if (killCountMap.containsKey(worldPoint)) {
                StringBuilder tooltip = new StringBuilder()
                        .append("X: ").append(worldPoint.getX())
                        .append(", Y: ").append(worldPoint.getY())
                        .append(", Plane: ").append(worldPoint.getPlane())
                        .append("</br>");

                for (Map.Entry<WorldPoint, HashMap<Integer, Integer>> entry : killCountMap.entrySet()) {
                    Map<Integer, Integer> npcHashMap = entry.getValue();

                    if (WorldPointHelper.equals(entry.getKey(), worldPoint)) {
                        int max = 0;

                        for (int value : npcHashMap.values()) {
                            if (value > max) {
                                max = value;
                            }
                        }

                        for (Map.Entry<Integer, Integer> entry1 : npcHashMap.entrySet()) {
                            int count = entry1.getValue();

                            tooltip
                                .append(ColorUtil.colorTag(Utilities.getHeatMapColor(count / (float) max)))
                                .append(entry1.getKey())
                                .append(": ")
                                .append(count)
                                .append("</br>");
                        }
                    }
                }

                tooltipManager.add(new Tooltip(tooltip.substring(0, tooltip.lastIndexOf("</br>"))));
            }
        }
    }

    private void renderTiles(Graphics2D graphics) {
        if (killCountMap != null) {
            int max = 0;

            for (Map<Integer, Integer> entry : killCountMap.values()) {
                for (int entry1 : entry.values()) {
                    if (entry1 > max) {
                        max = entry1;
                    }
                }
            }

            for (Map.Entry<WorldPoint, HashMap<Integer, Integer>> entry : killCountMap.entrySet()) {
                WorldPoint point = entry.getKey();
                Map<Integer, Integer> value = entry.getValue();
                LocalPoint tileLocation = LocalPoint.fromWorld(client, point.getX(), point.getY());

                if (value != null && tileLocation != null && point.getPlane() == client.getPlane()) {
                    renderTile(client, graphics, tileLocation, entry.getValue(), max);
                }
            }
        }
    }

    private void renderTile(Client client, Graphics2D graphics, LocalPoint tileLocation, Map<Integer, Integer> tileValue, int max) {
        Polygon polygon = Perspective.getCanvasTilePoly(client, tileLocation);

        if (polygon != null) {
            int renderValue = 0;

            for (int value : tileValue.values()) {
                renderValue += value;
            }

            OverlayUtil.renderPolygon(graphics, polygon, Utilities.getHeatMapColor((float) (renderValue / (double) max)));
        }
    }

    private void updateMaps() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received a kill update since the overlay last checked for one, repopulate the
        // local kill map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.isBefore(plugin.lastUpdatedKill))) {
            lastUpdated = plugin.lastUpdatedKill;
            killCountMap = database.retrieveKillMap(player);
        }
    }
}
