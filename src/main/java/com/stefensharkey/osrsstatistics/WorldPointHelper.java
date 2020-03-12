package com.stefensharkey.osrsstatistics;

import net.runelite.api.coords.WorldPoint;

public class WorldPointHelper {

    public static boolean equals(WorldPoint point1, WorldPoint point2) {
        if (point1 != null && point2 != null) {
            return point1.getX() == point2.getX() &&
                    point1.getY() == point2.getY() &&
                    point1.getPlane() == point2.getPlane();
        }

        return false;
    }
}
