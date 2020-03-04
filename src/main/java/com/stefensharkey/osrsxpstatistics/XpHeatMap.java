package com.stefensharkey.osrsxpstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.Skill;

import java.awt.*;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;

@Slf4j
public class XpHeatMap {

    private final XpStatisticsConfig config;

    public XpHeatMap(XpStatisticsConfig config) {
        this.config = config;
    }

    public void generateHeatMap() throws IOException, SQLException {
//        BufferedImage map = ImageIO.read(new File(RuneLite.RUNELITE_DIR, "map.png"));
//        Graphics2D graphics = map.createGraphics();
//        Point2D center = new Point2D.Float(getModifiedX(3230), getModifiedY(3219));
//        float[] dist = { 0.0F, 0.2F, 1.0F };
//        Color[] colors = { Color.RED, Color.WHITE, Color.BLUE };
//        RadialGradientPaint p = new RadialGradientPaint(center, config.heatMapDotSize() / 3.0F, dist, colors);
//
//        graphics.setPaint(p);
//        graphics.fillRect((int) center.getX(), (int) center.getY(), config.heatMapDotSize(), config.heatMapDotSize());
//        graphics.dispose();
//
//        File mapFile = new File(RuneLite.RUNELITE_DIR, "heatmap.png");
//        ImageIO.write(map, "png", mapFile);
        Database database = Database.getInstance();
        ResultSet results = database.retrieve("LordOfWoeHC");
        HashMap<Point, Integer> data = new HashMap<>();

        while (results.next()) {
            Point point = new Point(results.getInt("x_coord"), results.getInt("y_coord"));

            int newLength = Skill.values().length - 1;
            Skill[] skills = Arrays.copyOf(Skill.values(), newLength);
            int sum = 0;

            for (Skill skill : skills) {
                sum += results.getInt(skill.getName().toLowerCase());
            }

            data.put(point, sum);
        }

        log.info(data.toString());
    }

    private int getModifiedX (int x) {
        return (x * config.heatMapScale()) - config.heatMapOffsetX();
    }

    private int getModifiedY (int y) {
        return (y * config.heatMapScale()) - config.heatMapOffsetY();
    }

    private Color getHeatMapColor (float value) {
        int numColors = 3;
        float[][] colors = { { 0, 0, 1 }, { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 } };

        int index1;
        int index2;
        float fractionBetween = 0;

        if (value <= 0.0) {
            index1 = 0;
            index2 = 0;
        } else if (value >= 1) {
            index1 = numColors;
            index2 = numColors;
        } else {
            value = value * numColors;
            index1 = (int) Math.floor(value);
            index2 = index1 + 1;
            fractionBetween = value - (float) index1;
        }

        return new Color((colors[index2][0] - colors[index1][0]) * fractionBetween + colors[index1][0],
                (colors[index2][1] - colors[index1][1]) * fractionBetween + colors[index1][1],
                (colors[index2][2] - colors[index1][2]) * fractionBetween + colors[index1][2]);
    }
}
