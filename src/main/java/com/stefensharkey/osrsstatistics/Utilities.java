package com.stefensharkey.osrsstatistics;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Map;

@Slf4j
@UtilityClass
public class Utilities {

    public Color getHeatMapColor(float value) {
        float[][] colors = { { 0, 0, 1 }, { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 } };
        int numColors = colors.length - 1;

        int index1 = 0;
        int index2 = 0;
        float fractionBetween = 0;

        if (value >= 1) {
            index1 = numColors;
            index2 = numColors;
        } else if (value > 0) {
            value *= numColors;
            index1 = (int) Math.floor(value);
            index2 = index1 + 1;
            fractionBetween = value - index1;
        }

        return new Color((colors[index2][0] - colors[index1][0]) * fractionBetween + colors[index1][0],
                (colors[index2][1] - colors[index1][1]) * fractionBetween + colors[index1][1],
                (colors[index2][2] - colors[index1][2]) * fractionBetween + colors[index1][2]);
    }

    public void renderTiles(Client client, Graphics2D graphics, Actor player, Map<WorldPoint, ?> map) {
        LocalPoint localLocation = player.getLocalLocation();
        int maxDistance = 2350;

        if (map != null) {
            map.forEach((point, value) -> {
                LocalPoint tileLocation = LocalPoint.fromWorld(client, point.getX(), point.getY());
                int plane = point.getPlane();

                if (tileLocation != null
                        && plane == client.getPlane()
                        && localLocation.distanceTo(tileLocation) <= maxDistance) {
                    renderTile(client, graphics, tileLocation, value);
                }
            });
        }
    }

    private void renderTile(Client client, Graphics2D graphics, LocalPoint tileLocation, Object tileValue) {
        Polygon polygon = Perspective.getCanvasTilePoly(client, tileLocation);

        if (polygon != null) {
            Double[] renderValue = {0.0};

            if (tileValue instanceof Map) {
                ((Map) tileValue).forEach((key, value) -> renderValue[0] += (double) value);
            } else if (tileValue instanceof Double) {
                renderValue[0] = (double) tileValue;
            }

            OverlayUtil.renderPolygon(graphics, polygon, getHeatMapColor(renderValue[0].floatValue()));
        }
    }
}
