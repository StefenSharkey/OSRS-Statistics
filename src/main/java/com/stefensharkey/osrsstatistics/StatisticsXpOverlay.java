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
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class StatisticsXpOverlay extends Overlay {

    private final Client client;
    private final StatisticsPlugin plugin;
    private final StatisticsConfig config;
    private final TooltipManager tooltipManager;
    private final Database database;

    private Map<WorldPoint, EnumMap<Skill, Integer[]>> xpMap;
    private int tileIndex;
    private int tooltipIndex;

    private LocalDateTime lastUpdated;

    @Inject
    StatisticsXpOverlay(Client client, StatisticsPlugin plugin, StatisticsConfig config, TooltipManager tooltipManager) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;
        database = new Database(config);
        updateMaps();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.isXpOverlayEnabled()) {
            updateMaps();

            Player player = client.getLocalPlayer();

            if (player != null) {
                tileIndex = config.shouldXpOverlayShowTotal() ? 0 : 1;
                tooltipIndex = config.shouldXpTooltipHighlightTotal() ? 0 : 1;

                renderTiles(graphics);

                if (config.isXpTooltipEnabled()) {
                    renderTooltip();
                }
            }
        }

        return null;
    }

    private void renderTooltip() {
        Tile selectedTile = client.getSelectedSceneTile();

        if (selectedTile != null) {
            WorldPoint worldPoint = selectedTile.getWorldLocation();

            if (xpMap.containsKey(worldPoint)) {
                StringBuilder tooltip = new StringBuilder()
                        .append("X: ").append(worldPoint.getX())
                        .append(", Y: ").append(worldPoint.getY())
                        .append(", Plane: ").append(worldPoint.getPlane())
                        .append("</br>");

                for (Map.Entry<WorldPoint, EnumMap<Skill, Integer[]>> entry : xpMap.entrySet()) {
                    Map<Skill, Integer[]> skillEnumMap = entry.getValue();

                    int max = 0;

                    for (Integer[] tileValues : skillEnumMap.values()) {
                        if (tileValues[tooltipIndex] > max) {
                            max = tileValues[tooltipIndex];
                        }
                    }

                    if (WorldPointHelper.equals(entry.getKey(), worldPoint)) {
                        for (Map.Entry<Skill, Integer[]> entry1 : skillEnumMap.entrySet()) {
                            Integer[] experience = entry1.getValue();

                            if (experience[0] > 0) {
                                tooltip
                                    .append(ColorUtil.colorTag(
                                            Utilities.getHeatMapColor(experience[tooltipIndex] / (float) max)))
                                    .append(entry1.getKey().getName())
                                    .append(": ")
                                    .append(experience[0])
                                    .append(" (")
                                    .append(experience[1])
                                    .append(")</br>");
                            }
                        }
                    }
                }

                tooltipManager.add(new Tooltip(tooltip.substring(0, tooltip.lastIndexOf("</br>"))));
            }
        }
    }

    private void renderTiles(Graphics2D graphics) {
        if (xpMap != null) {
            int max = 0;

            for (Map<Skill, Integer[]> entry : xpMap.values()) {
                for (Integer[] entry1 : entry.values()) {
                    if (entry1[tileIndex] > max) {
                        max = entry1[tileIndex];
                    }
                }
            }

            for (Map.Entry<WorldPoint, EnumMap<Skill, Integer[]>> entry : xpMap.entrySet()) {
                WorldPoint point = entry.getKey();
                Map<Skill, Integer[]> value = entry.getValue();

                LocalPoint tileLocation = LocalPoint.fromWorld(client, point.getX(), point.getY());

                if (value != null && tileLocation != null && point.getPlane() == client.getPlane()) {
                    renderTile(graphics, tileLocation, value, max);
                }
            }
        }
    }

    private void renderTile(Graphics2D graphics, LocalPoint tileLocation, Map<Skill, Integer[]> tileValue, int max) {
        Polygon polygon = Perspective.getCanvasTilePoly(client, tileLocation);

        if (polygon != null) {
            double renderValue = 0.0;

            for (Integer[] value : tileValue.values()) {
                renderValue += value[tileIndex];
            }

            OverlayUtil.renderPolygon(graphics, polygon, Utilities.getHeatMapColor((float) (renderValue / max)));
        }
    }

    private void updateMaps() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received an XP update since the overlay last checked for one, repopulate the
        // local XP map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.isBefore(plugin.lastUpdatedXp))) {
            lastUpdated = plugin.lastUpdatedXp;

            xpMap = database.retrieveXpMap(client);
        }
    }
}
