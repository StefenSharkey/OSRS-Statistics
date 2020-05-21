package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
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
                Utilities.renderTiles(client, graphics, player, killCountMap);
            }

            if (config.isKillTooltipEnabled()) {
                renderTooltip();
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

                killCountMap.forEach((point, npcHashMap) -> {
                    if (WorldPointHelper.equals(point, worldPoint)) {
                        int max = npcHashMap
                                .values()
                                .stream()
                                .mapToInt(v -> v)
                                .filter(entry -> entry >= 0)
                                .max()
                                .orElse(0);

                        npcHashMap.forEach((npcName, count) -> {
                            tooltip.append(ColorUtil.colorTag(Utilities.getHeatMapColor(count / (float) max)))
                                    .append(npcName)
                                    .append(": ")
                                    .append(count)
                                    .append("</br>");
                        });
                    }
                });

                tooltipManager.add(new Tooltip(tooltip.substring(0, tooltip.lastIndexOf("</br>"))));
            }
        }
    }

    private void updateMaps() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received a kill update since the overlay last checked for one, repopulate the
        // local kill map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.isBefore(plugin.lastUpdatedKill))) {
            lastUpdated = plugin.lastUpdatedKill;
            killCountMap = database.retrieveKillMap(client.getUsername(), false);
        }
    }
}
