package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;

@Slf4j
public class HeatMap implements Runnable {

    private final Client CLIENT;
    private final StatisticsConfig CONFIG;
    private final Database DATABASE;

    static boolean isGenerating;

    HeatMap(Client client, StatisticsConfig config) {
        CLIENT = client;
        CONFIG = config;
        DATABASE = new Database(config);
    }

    @Override
    public void run() {
        try {
            isGenerating = true;
            CLIENT.addChatMessage(ChatMessageType.GAMEMESSAGE, "","Heat map is generating...", null);

            HashMap<WorldPoint, Float> data = DATABASE.retrieveXpCountMap(CLIENT.getUsername(), false, true);
            BufferedImage map = ImageIO.read(getMap());
            Graphics2D graphics = map.createGraphics();

            float[] dist = {0.0F, 1.0F};
            Color[] colors = {new Color(255, 0, 0, 255), new Color(255, 0, 0, 0)};

            data.forEach((point, xpValue) -> {
                // Instead of flipping the image vertically to account for origin differences, we subtract the point's
                // vertical coordinate from the image's height.
                drawGradientCircle(graphics, new Point(point.getX(), map.getHeight() - point.getY()), CONFIG.heatMapDotSize(), dist, colors);
            });

            graphics.dispose();

            File mapFile = new File(RuneLite.RUNELITE_DIR, "heatmap.png");
            ImageIO.write(map, "png", mapFile);

            CLIENT.addChatMessage(ChatMessageType.GAMEMESSAGE, "","Heat map generation finished.", null);
        } catch (IOException e) {
            log.error(e.getMessage());

            CLIENT.addChatMessage(ChatMessageType.GAMEMESSAGE, "","Heat map generation failed.", null);
        } finally {
            isGenerating = false;
        }
    }

    private void drawGradientCircle(Graphics2D graphics, Point2D point, float radius, float[] dist, Color[] colors) {
        Paint radialGradientPaint = new RadialGradientPaint(point, radius, dist, colors);
        graphics.setPaint(radialGradientPaint);
        graphics.fill(new Ellipse2D.Double(point.getX() - radius, point.getY() - radius, radius * 2, radius * 2));
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
}
