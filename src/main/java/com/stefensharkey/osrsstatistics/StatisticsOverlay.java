package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;

@Slf4j
public class StatisticsOverlay extends Overlay {

    private static final int MAX_DISTANCE = 2350;

    private final Client CLIENT;
    private final StatisticsPlugin PLUGIN;
    private final StatisticsConfig CONFIG;
    private final TooltipManager TOOLTIP_MANAGER;
    private final Database DATABASE;

    private LinkedHashMap<WorldPoint, EnumMap<Skill, Integer[]>> xpDeltaMap;
    private LinkedHashMap<WorldPoint, EnumMap<Skill, Double>> xpTotalMap;
    private LinkedHashMap<WorldPoint, Double> xpCountMap;
    private Date lastUpdated;

    private boolean displayXpTotal;

    @Inject
    StatisticsOverlay(Client client, StatisticsPlugin plugin, StatisticsConfig config, TooltipManager tooltipManager) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        CLIENT = client;
        PLUGIN = plugin;
        CONFIG = config;
        TOOLTIP_MANAGER = tooltipManager;
        DATABASE = new Database(config);
        displayXpTotal = CONFIG.displayXpTotal();
        updateTiles();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        updateTiles();

        if (CONFIG.displayXpTileOverlay()) {
            Player player = CLIENT.getLocalPlayer();

            if (player != null) {
                renderTiles(graphics, player);
            }

            Tile selectedTile = CLIENT.getSelectedSceneTile();
            if (CONFIG.displayTooltip() && selectedTile != null) {
                WorldPoint worldPoint = selectedTile.getWorldLocation();

                if (xpDeltaMap.containsKey(worldPoint)) {
                    StringBuilder tooltip = new StringBuilder()
                            .append("X: ").append(worldPoint.getX())
                            .append(", Y: ").append(worldPoint.getY())
                            .append(", Plane: ").append(worldPoint.getPlane())
                            .append("</br>");

                    xpDeltaMap
                            .entrySet()
                            .stream()
                            .filter(entry -> WorldPointHelper.equals(entry.getKey(), worldPoint))
                            .flatMap(entry -> entry.getValue().entrySet().stream())
                            .forEach(entry1 -> tooltip
                                    .append(entry1.getKey().getName())
                                    .append(": ")
                                    .append(entry1.getValue()[0])
                                    .append("(")
                                    .append(entry1.getValue()[1])
                                    .append(")</br>"));

//                    xpTotalMap
//                            .entrySet()
//                            .stream()
//                            .filter(e -> WorldPointHelper.equals(e.getKey(), worldLocation))
//                            .collect(Collectors.toSet())
//                            .stream()
//                            .map(Map.Entry::getValue)
//                            .collect(Collectors.toList())

                    tooltip.substring(0, tooltip.lastIndexOf("</br>"));

                    TOOLTIP_MANAGER.add(new Tooltip(tooltip.toString()));
                }
            }
        }

        return null;
    }

    private void renderTiles(Graphics2D graphics, Actor player) {
        LocalPoint localLocation = player.getLocalLocation();
        LinkedHashMap<WorldPoint, ?> map = null;

        if (xpCountMap != null && xpTotalMap != null) {
            map = CONFIG.displayXpTotal() ? xpTotalMap : xpCountMap;
        }

        if (map != null) {
            map.forEach((point, value) -> {
                LocalPoint tileLocation = LocalPoint.fromWorld(CLIENT, point.getX(), point.getY());
                int plane = point.getPlane();

                if (tileLocation != null && plane == CLIENT.getPlane() && localLocation.distanceTo(tileLocation) <= MAX_DISTANCE) {
                    renderTile(graphics, tileLocation, value);
                }
            });
        }
    }

    private void renderTile(Graphics2D graphics, LocalPoint tileLocation, Object value) {
        Polygon polygon = Perspective.getCanvasTilePoly(CLIENT, tileLocation);

        if (polygon != null) {
            Double[] renderValue = {0.0};

            if (CONFIG.displayXpTotal()) {
                ((EnumMap<Skill, Double>) value).forEach((skill, xpValue) -> renderValue[0] += xpValue);
            } else {
                renderValue[0] = (double) value;
            }

            OverlayUtil.renderPolygon(graphics, polygon, getHeatMapColor(renderValue[0].floatValue()));
        }
    }

    private void updateTiles() {
        Actor player = CLIENT.getLocalPlayer();

        // If the player exists, and has received an XP update since the overlay last checked for one, repopulate the
        // local XP map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.before(PLUGIN.lastUpdated) || displayXpTotal != CONFIG.displayXpTotal())) {
            lastUpdated = PLUGIN.lastUpdated;
            displayXpTotal = CONFIG.displayXpTotal();

            xpDeltaMap = DATABASE.retrieveXpDeltaMap(player.getName(), false);
            xpTotalMap = DATABASE.retrieveXpTotalMap(player.getName(), true, false);
            xpCountMap = DATABASE.retrieveXpCountMap(player.getName(), true, false);
        }
    }

    private Color getHeatMapColor (float value) {
        int numColors = 3;
        float[][] colors = { { 0, 0, 1 }, { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 } };

        int index1;
        int index2;
        float fractionBetween = 0;

        if (value <= 0) {
            index1 = 0;
            index2 = 0;
        } else if (value >= 1) {
            index1 = numColors;
            index2 = numColors;
        } else {
            value *= numColors;
            index1 = (int) Math.floor(value);
            index2 = index1 + 1;
            fractionBetween = value - index1;
        }

        return new Color((colors[index2][0] - colors[index1][0]) * fractionBetween + colors[index1][0],
                (colors[index2][1] - colors[index1][1]) * fractionBetween + colors[index1][1],
                (colors[index2][2] - colors[index1][2]) * fractionBetween + colors[index1][2]);
    }

}
