package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
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
public class HeatMap implements Runnable {

    private final int PIXELS_PER_TILE = 4;
    private final int OFFSET_X = 1152;
    private final int OFFSET_Y = 1216;

    private Client client;
    private StatisticsConfig config;

    public static boolean isGenerating = false;

    public HeatMap(Client client, StatisticsConfig config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            isGenerating = true;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "","Heat map is generating...", null);

            HashMap<Point, Integer> data = getData(new Database(config).retrieveXp("LordOfWoeBTW"));
            BufferedImage map = ImageIO.read(getMap());
            Graphics2D graphics = map.createGraphics();

            float[] dist = {0.0F, 1.0F};
            Color[] colors = {new Color(255, 0, 0, 255), new Color(255, 0, 0, 0)};

            data.forEach((point, xpValue) -> {
                // Instead of flipping the image vertically to account for origin differences, we subtract the point's
                // vertical coordinate from the image's height.
                point.y = map.getHeight() - point.y;
                drawGradientCircle(graphics, point, config.heatMapDotSize(), dist, colors);
            });

            graphics.dispose();

            File mapFile = new File(RuneLite.RUNELITE_DIR, "heatmap.png");
            ImageIO.write(map, "png", mapFile);

            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "","Heat map generation finished.", null);
        } catch (SQLException | IOException e) {
            e.printStackTrace();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "","Heat map generation failed.", null);
        } finally {
            isGenerating = false;
        }
    }

    private void drawGradientCircle(Graphics2D graphics, Point2D point, float radius, float[] dist, Color[] colors) {
        RadialGradientPaint radialGradientPaint = new RadialGradientPaint(point, radius, dist, colors);
        graphics.setPaint(radialGradientPaint);
        graphics.fill(new Ellipse2D.Double(point.getX() - radius, point.getY() - radius, radius * 2, radius * 2));
    }

    private HashMap<Point, Integer> getData(ResultSet results) throws SQLException {
        HashMap<Point, Integer> newData = new HashMap<>();

        while (results.next()) {
            Point point = new Point((int) Math.round(getModifiedX(results.getInt("x_coord"))),
                    (int) Math.round(getModifiedY(results.getInt("y_coord"))));

            int newLength = Skill.values().length - 1;
            Skill[] skills = Arrays.copyOf(Skill.values(), newLength);
            int sum = 0;

            for (Skill skill : skills) {
                sum += results.getInt(skill.getName().toLowerCase());
            }

            newData.put(point, sum);
        }

        return newData;
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

    private double getModifiedX (int x) {
        return (x - OFFSET_X) * PIXELS_PER_TILE;
    }

    private double getModifiedY (int y) {
        return (y - OFFSET_Y) * PIXELS_PER_TILE;
    }
}