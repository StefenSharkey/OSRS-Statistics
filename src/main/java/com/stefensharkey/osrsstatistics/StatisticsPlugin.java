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

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;

@Slf4j
@PluginDescriptor(
        name = "Statistics"
)
public class StatisticsPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private StatisticsConfig config;

    @Inject
    private StatisticsKillOverlay killOverlay;

    @Inject
    private StatisticsXpOverlay xpOverlay;

    private final LinkedHashMap<Skill, Integer> skillXpCache = new LinkedHashMap<>();

    private Database database;

    Date lastUpdatedKill = new Date();
    Date lastUpdatedXp = new Date();

    @Override
    protected void startUp() {
        database = new Database(config);
        overlayManager.add(killOverlay);
        overlayManager.add(xpOverlay);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(killOverlay);
        overlayManager.remove(xpOverlay);
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        Skill skill = statChanged.getSkill();
        int xp = statChanged.getXp();

        // We don't care about the overall skill or experience, as it can be calculated.
        if (skill != Skill.OVERALL) {
            // When a player logs in, this method is fired for each skill. We can use this to populate the cache.
            if (skillXpCache.size() < (Skill.values().length - 1)) {
                skillXpCache.put(skill, xp);
            } else {
                // Occasionally, this method will be fired undesirably. To counteract this, only proceed if there is
                // actually an XP change.
                if (skillXpCache.get(skill) != xp) {
                    skillXpCache.put(skill, xp);
                    Player player = client.getLocalPlayer();

                    // Sanity check for player nullability.
                    if (player != null) {
                        WorldPoint location = player.getWorldLocation();
                        lastUpdatedXp = new Date();

                        // Required for the thread; otherwise, the client may be reset.
                        int world = client.getWorld();

                        // Insert into the database within a thread so
                        new Thread(() -> database.insertXp(player.getName(),
                                new Timestamp(lastUpdatedKill.getTime()),
                                skillXpCache,
                                location.getX(),
                                location.getY(),
                                location.getPlane(),
                                world)).start();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();

        /* In order to accurately maintain XP cache, we must reset the cache only as the user is logging in.
           If we use GameState.LOGGED_IN instead we would occasionally reset the cache without re-initializing it. This
           is because, when the user does certain actions such as loading into a new region, this method is fired for
           GameState.LOGGED_IN, without firing GameStatChanged event for all skills. */
        if (gameState == GameState.LOGGING_IN) {
            clearXpCache();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        NPC npc = npcDespawned.getNpc();

        if (npc.isDead()) {
            Player player = client.getLocalPlayer();

            if (player != null) {
                WorldPoint location = player.getWorldLocation();
                lastUpdatedKill = new Date();

                // Required for the thread; otherwise, the insertion becomes invalid, as the NPC is reset.
                String name = npc.getName();
                int level = npc.getCombatLevel();
                int world = client.getWorld();

                log.info("npc={}", npc);

                new Thread(() -> database.insertKill(player.getName(),
                        new Timestamp(lastUpdatedKill.getTime()),
                        name,
                        level,
                        location.getX(),
                        location.getY(),
                        location.getPlane(),
                        world)).start();
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        switch (configChanged.getKey()) {
            case "databasetype":
            case "databaseserverip":
            case "databaseusername":
            case "databasepassword":
            case "databasename":
            case "databasetableprefix":
                database.updateConfig(config);
                break;
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted commandExecuted) {
        switch (commandExecuted.getCommand().toLowerCase()) {
            case "xpheat":
                if (HeatMap.isGenerating) {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE,
                            "",
                            "A heat map is aleady generating. Please wait for it to complete.",
                            null);
                } else {
                    new Thread(new HeatMap(client, config)).start();
                }
                break;
        }
    }

    @Provides
    StatisticsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(StatisticsConfig.class);
    }

    private void clearXpCache() {
        skillXpCache.clear();
    }
}
