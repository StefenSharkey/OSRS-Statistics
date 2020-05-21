package com.stefensharkey.osrsstatistics;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;

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
}
