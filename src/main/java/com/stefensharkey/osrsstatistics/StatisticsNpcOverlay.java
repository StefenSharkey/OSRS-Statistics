package com.stefensharkey.osrsstatistics;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Shape;
import java.sql.ResultSet;
import java.time.LocalDateTime;

@Slf4j
public class StatisticsNpcOverlay extends Overlay {

    private final Client client;
    private final StatisticsPlugin plugin;
    private final StatisticsConfig config;
    private final TooltipManager tooltipManager;
    private final ItemManager itemManager;
    private final Database database;

    private ResultSet loot;
    private LocalDateTime lastUpdatedLoot;

    private ResultSet kills;
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
        database = new Database(config);
        updateMaps();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.isNpcTooltipEnabled() && !client.isMenuOpen()) {
            updateMaps();

            Point mouseCanvasPoint =
                    new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());

            for (NPC npc : client.getNpcs()) {
                Polygon npcTilePoly = npc.getCanvasTilePoly();
                Shape npcHull = npc.getConvexHull();

                if ((npcHull != null && npcHull.contains(mouseCanvasPoint))
                    || (npcTilePoly != null && npcTilePoly.contains(mouseCanvasPoint))) {
                    renderTooltip(npc);
                }
            }
        }

        return null;
    }

    @SneakyThrows
    private void renderTooltip(NPC npc) {
        StringBuilder tooltip = new StringBuilder();
        int numKills = 0;

        // Determine the number of kills.
        while (kills.next() && kills.getInt("npc_id") == npc.getId()) {
            numKills++;
        }

        tooltip.append("Kills: ").append(numKills).append("</br></br>Loot:</br>");

        // Determine the loot names and quantities.
        while (loot.next() && loot.getInt("npc_id") == npc.getId()) {
            tooltip.append(itemManager.getItemComposition(loot.getInt("item_id")).getName())
                    .append(" (")
                    .append(loot.getInt("quantity"))
                    .append(")</br>");
        }

        // Reset cursors for next iteration.
        kills.beforeFirst();
        loot.beforeFirst();

        tooltipManager.add(new Tooltip(tooltip.toString()));
    }

    private void updateMaps() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received a loot update since the overlay last checked for one, repopulate the
        // local loot map and make note of it.
        if (player != null && (lastUpdatedLoot == null || lastUpdatedLoot.isBefore(plugin.lastUpdatedLoot))) {
            lastUpdatedLoot = plugin.lastUpdatedLoot;

            loot = database.retrieveLoot(player.getName());
        }

        // If the player exists, and has received a kill update since the overlay last checked for one, repopulate the
        // local kill map and make note of it.
        if (player != null && (lastUpdatedKill == null || lastUpdatedKill.isBefore(plugin.lastUpdatedKill))) {
            lastUpdatedKill = plugin.lastUpdatedKill;
            kills = database.retrieveKill(client.getUsername());
        }
    }
}
