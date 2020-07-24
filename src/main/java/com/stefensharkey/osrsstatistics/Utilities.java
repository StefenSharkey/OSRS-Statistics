package com.stefensharkey.osrsstatistics;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.awt.Color;

@Slf4j
@UtilityClass
public class Utilities {

    public static Color getHeatMapColor(float value) {
        float[][] colors = {{0.0F, 0.0F, 1.0F}, {0.0F, 1.0F, 0.0F}, {1.0F, 1.0F, 0.0F}, {1.0F, 0.0F, 0.0F}};
        int numColors = colors.length - 1;

        int index1 = 0;
        int index2 = 0;
        float fractionBetween = 0.0F;

        if (Float.compare(value, 1.0F) >= 0) {
            index1 = numColors;
            index2 = numColors;
        } else if (Float.compare(value, 0.0F) > 0) {
            value *= numColors;
            index1 = (int) Math.floor(value);
            index2 = index1 + 1;
            fractionBetween = value - index1;
        }

        return new Color((colors[index2][0] - colors[index1][0]) * fractionBetween + colors[index1][0],
                (colors[index2][1] - colors[index1][1]) * fractionBetween + colors[index1][1],
                (colors[index2][2] - colors[index1][2]) * fractionBetween + colors[index1][2]);
    }

    /**
     * Compares the two specified {@code WorldPoints}s for equality.
     * Returns {@code true} if the two points represent the same point.
     *
     * @param point1 first world point to be compared for equality
     * @param point2 second world point to be compared for equality
     * @return {@code true} if the two specified points are equal
     */
    public static boolean worldPointEquals(WorldPoint point1, WorldPoint point2) {
        if (point1 != null && point2 != null) {
            return point1.getX() == point2.getX() &&
                   point1.getY() == point2.getY() &&
                   point1.getPlane() == point2.getPlane();
        }

        return false;
    }
}
