package com.stefensharkey.osrsstatistics;

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
import java.sql.SQLException;
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
    private LocalDateTime lastUpdated;

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
        updateLoot();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.isNpcTooltipEnabled()) {
            if (client.isMenuOpen()) {
                return null;
            }

            updateLoot();

            Point mouseCanvasPoint =
                    new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());

            client.getNpcs().forEach(npc -> {
                Polygon npcTilePoly = npc.getCanvasTilePoly();
                Shape npcHull = npc.getConvexHull();

                if ((npcHull != null && npcHull.contains(mouseCanvasPoint))
                        || (npcTilePoly != null && npcTilePoly.contains(mouseCanvasPoint))) {
                    renderTooltip(npc);
                }
            });
        }

        return null;
    }

    private void renderTooltip(NPC npc) {
        try {
            StringBuilder tooltip = new StringBuilder();
            String npcName = npc.getName();

            while (loot.next()) {
                String name = loot.getString("npc_name");
                int level = loot.getInt("npc_level");

                if (npcName != null && npcName.equals(name) && npc.getCombatLevel() == level) {
                    int id = loot.getInt("item_id");
                    int quantity = loot.getInt("quantity");

                    tooltip.append(itemManager.getItemComposition(id).getName())
                            .append(" (").append(quantity)
                            .append(")</br>");
                }
            }

            loot.beforeFirst();

            if (tooltip.length() > 0) {
                tooltipManager.add(new Tooltip(tooltip.toString()));
            }
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }
    }

    private void updateLoot() {
        Actor player = client.getLocalPlayer();

        // If the player exists, and has received an XP update since the overlay last checked for one, repopulate the
        // local XP map and make note of it.
        if (player != null && (lastUpdated == null || lastUpdated.isBefore(plugin.lastUpdatedLoot))) {
            lastUpdated = plugin.lastUpdatedLoot;

            loot = database.retrieveLoot(player.getName());
        }
    }
}
