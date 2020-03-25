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
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;

@Slf4j
public class StatisticsXpOverlay extends Overlay {

    private final Client client;
    private final StatisticsPlugin plugin;
    private final StatisticsConfig config;
    private final TooltipManager tooltipManager;
    private final Database database;

    private LinkedHashMap<WorldPoint, EnumMap<Skill, Integer[]>> xpDeltaMap;
    private LinkedHashMap<WorldPoint, EnumMap<Skill, Double>> xpTotalMap;
    private LinkedHashMap<WorldPoint, Double> xpCountMap;
    private Date lastUpdated;

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
        if (config.xpOverlayEnabled()) {
            updateMaps();

            Player player = client.getLocalPlayer();

            if (player != null) {
                Utilities.renderTiles(client, graphics, player, config.xpOverlayShowTotal() ? xpTotalMap : xpCountMap);
            }

            if (config.xpTooltipEnabled()) {
                renderTooltip();
            }
        }

        return null;
    }

    private void renderTooltip() {
        Tile selectedTile = client.getSelectedSceneTile();

        if (selectedTile != null) {
            WorldPoint worldPoint = selectedTile.getWorldLocation();

            if (xpDeltaMap.containsKey(worldPoint)) {
                StringBuilder tooltip = new StringBuilder()
                        .append("X: ").append(worldPoint.getX())
                        .append(", Y: ").append(worldPoint.getY())
                        .append(", Plane: ").append(worldPoint.getPlane())
                        .append("</br>");

                xpDeltaMap.forEach((point, skillEnumMap) -> {
                    if (WorldPointHelper.equals(point, worldPoint)) {
                        int index = config.xpTooltipHighlightTotal() ? 0 : 1;

                        int max = skillEnumMap
                                .values()
                                .stream()
                                .map(integers -> integers[index])
                                .mapToInt(entry -> entry)
                                .filter(entry -> entry >= 0)
                                .max()
                                .orElse(0);

                        skillEnumMap.forEach((skill, values) -> {
                            if (values[0] > 0) {
                                tooltip
                                    .append(ColorUtil.colorTag(Utilities.getHeatMapColor(values[index] / (float) max)))
                                    .append(skill.getName())
                                    .append(": ")
                                    .append(values[0])
                                    .append(" (")
                                    .append(values[1])
                                    .append(")</br>");
                            }
                        });
                    }
                });

                tooltipManager.add(new Tooltip(tooltip.substring(0, tooltip.lastIndexOf("</br>"))));
            }
        }
    }

    private void updateMaps() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received an XP update since the overlay last checked for one, repopulate the
        // local XP map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.before(plugin.lastUpdatedXp))) {
            lastUpdated = plugin.lastUpdatedXp;

            xpDeltaMap = database.retrieveXpDeltaMap(player.getName(), false);
            xpTotalMap = database.retrieveXpTotalMap(player.getName(), true, false);
            xpCountMap = database.retrieveXpCountMap(player.getName(), true, false);
        }
    }
}
