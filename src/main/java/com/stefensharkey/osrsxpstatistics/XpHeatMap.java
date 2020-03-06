package com.stefensharkey.osrsxpstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
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

        Database database = new Database(config);
        ResultSet results = database.retrieveXp("LordOfWoeBTW");
        HashMap<Point, Integer> data = new HashMap<>();
        int max = 0;

        while (results.next()) {
            Point point = new Point(getModifiedX(results.getInt("x_coord")), getModifiedY(results.getInt("y_coord")));

            int newLength = Skill.values().length - 1;
            Skill[] skills = Arrays.copyOf(Skill.values(), newLength);
            int sum = 0;

            for (Skill skill : skills) {
                sum += results.getInt(skill.getName().toLowerCase());
            }

            if (sum > max) {
                max = sum;
            }

            data.put(point, sum);
        }

        BufferedImage map = ImageIO.read(getMap());
        Graphics2D graphics = map.createGraphics();
        float[] dist = { 0.0F, 1.0F };
        Color[] colors = { new Color(255, 0, 0, 255), new Color(255, 0, 0, 0) };

        data.forEach((point, xpValue) -> {
            drawGradientCircle(graphics, point, 20.0F, dist, colors);
        });

        graphics.dispose();

        File mapFile = new File(RuneLite.RUNELITE_DIR, "heatmap.png");
        ImageIO.write(map, "png", mapFile);
    }

    private void drawGradientCircle(Graphics2D graphics, Point2D center, float radius, float[] dist, Color[] colors) {
        RadialGradientPaint radialGradientPaint = new RadialGradientPaint(center, radius, dist, colors);
        graphics.setPaint(radialGradientPaint);
        graphics.fill(new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, radius * 2, radius * 2));
    }

    private File getMap() {
        File file = new File(RuneLite.RUNELITE_DIR, "map.png");

        if (!file.exists()) {
            String mapUrl = "https://cdn.runescape.com/assets/img/external/oldschool/web/osrs_world_map_july18_2019.PNG";

            try (ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(mapUrl).openStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            } catch (IOException e) {
                log.error(e.getLocalizedMessage());
            }
        }

        return file;
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
