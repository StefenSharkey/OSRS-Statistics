package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Date;
import java.util.HashMap;

@Slf4j
public class StatisticsOverlay extends Overlay {

    private static final int MAX_DISTANCE = 2350;

    private final Client CLIENT;
    private final StatisticsPlugin PLUGIN;
    private final StatisticsConfig CONFIG;
    private final Database DATABASE;

    private HashMap<WorldPoint, Float> xpTiles;
    private Date lastUpdated;

    @Inject
    StatisticsOverlay(Client client, StatisticsPlugin plugin, StatisticsConfig config) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        CLIENT = client;
        PLUGIN = plugin;
        CONFIG = config;
        DATABASE = new Database(config);
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
        }

        return null;
    }

    private void renderTiles(Graphics2D graphics, Actor player) {
        LocalPoint localLocation = player.getLocalLocation();

        if (xpTiles != null) {
            xpTiles.forEach((point, value) -> {
                LocalPoint tileLocation = LocalPoint.fromWorld(CLIENT, point.getX(), point.getY());
                int plane = point.getPlane();

                if (tileLocation != null && plane == CLIENT.getPlane() && localLocation.distanceTo(tileLocation) <= MAX_DISTANCE) {
                    renderTile(graphics, tileLocation, value);
                }
            });
        }
    }

    private void renderTile(Graphics2D graphics, LocalPoint tileLocation, float value) {
        Polygon polygon = Perspective.getCanvasTilePoly(CLIENT, tileLocation);

        if (polygon != null) {
            OverlayUtil.renderPolygon(graphics, polygon, getHeatMapColor(value));
        }
    }

    private void updateTiles() {
        Actor player = CLIENT.getLocalPlayer();

        // If the player exists, and has received an XP update since the overlay last checked for one, repopulate the
        // local XP map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.before(PLUGIN.lastUpdated))) {
            lastUpdated = PLUGIN.lastUpdated;
            xpTiles = DATABASE.retrieveXpCountMap(player.getName(), true, false);
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
