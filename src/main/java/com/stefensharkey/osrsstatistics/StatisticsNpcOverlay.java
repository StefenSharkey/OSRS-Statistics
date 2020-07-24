package com.stefensharkey.osrsstatistics;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
public class StatisticsNpcOverlay extends Overlay {

    private final Client client;
    private final StatisticsPlugin plugin;
    private final StatisticsConfig config;
    private final TooltipManager tooltipManager;
    private final ItemManager itemManager;

    private Map<Integer, Map<Integer, Integer>> loot;
    private LocalDateTime lastUpdatedLoot;

    private Map<WorldPoint, Map<Integer, Integer>> kills;
    private LocalDateTime lastUpdatedKill;

    @Inject
    StatisticsNpcOverlay(Client client, StatisticsPlugin plugin, StatisticsConfig config, TooltipManager tooltipManager, ItemManager itemManager) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;
        this.itemManager = itemManager;
        updateMaps();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.isNpcTooltipEnabled() && !client.isMenuOpen() && plugin.hoveredNpc != null) {
            updateMaps();
            renderTooltip(plugin.hoveredNpc);
        }

        return null;
    }

    @SneakyThrows
    private void renderTooltip(NPC npc) {
        StringBuilder tooltip = new StringBuilder("Kills");
        int numKills = 0;

        // Determine the number of kills.
        for (Map<Integer, Integer> npcEntry : kills.values()) {
            numKills += npcEntry.getOrDefault(npc.getId(), 0);
        }

        tooltip.append(numKills).append("</br></br>Loot:</br>");

        // Determine the loot names and quantities.
        for (Map.Entry<Integer, Map<Integer, Integer>> npcEntry : loot.entrySet()) {
            if (npcEntry.getKey() == npc.getId()) {
                for (Map.Entry<Integer, Integer> itemEntry : npcEntry.getValue().entrySet()) {
                    tooltip.append(itemManager.getItemComposition(itemEntry.getKey()).getName())
                            .append(" (")
                            .append(itemEntry.getValue())
                            .append(")</br>");
                }
            }
        }

        // Display tooltip, cutting off the final line break.
        tooltipManager.add(new Tooltip(tooltip.substring(0, tooltip.length() - 4)));
    }

    private void updateMaps() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received a loot update since the overlay last checked for one, repopulate the
        // local loot map and make note of it.
        if (player != null && (lastUpdatedLoot == null || lastUpdatedLoot.isBefore(plugin.lastUpdatedLoot))) {
            lastUpdatedLoot = plugin.lastUpdatedLoot;

            loot = plugin.database.retrieveLootMap(player.getName());
        }

        // If the player exists, and has received a kill update since the overlay last checked for one, repopulate the
        // local kill map and make note of it.
        if (player != null && (lastUpdatedKill == null || lastUpdatedKill.isBefore(plugin.lastUpdatedKill))) {
            lastUpdatedKill = plugin.lastUpdatedKill;
            kills = plugin.database.retrieveKillMap(player, false);
        }
    }
}
