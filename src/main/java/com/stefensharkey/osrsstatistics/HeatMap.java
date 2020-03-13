/*
 * Copyright (c) 2020, Stefen Sharkey <https://github.com/StefenSharkey>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
import java.util.LinkedHashMap;

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

            LinkedHashMap<WorldPoint, Double> data = DATABASE.retrieveXpCountMap(CLIENT.getUsername(), false, true);
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
            //noinspection HardcodedFileSeparator
            log.error("Map file I/O failed.", e);

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
                log.error("Could not download map file.", e);
            }
        }

        return file;
    }
}
