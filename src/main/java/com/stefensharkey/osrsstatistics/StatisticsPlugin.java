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
    private StatisticsOverlay overlay;

    private final LinkedHashMap<Skill, Integer> skillXpCache = new LinkedHashMap<>();

    private Database database;

    Date lastUpdated = new Date();

    @Override
    protected void startUp() {
        database = new Database(config);
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
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
                        lastUpdated = new Date();

                        database.insertXp(player.getName(),
                                new Timestamp(lastUpdated.getTime()),
                                skillXpCache,
                                location.getX(),
                                location.getY(),
                                location.getPlane(),
                                client.getWorld());
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

                database.insertKill(player.getName(),
                        new Timestamp(new Date().getTime()),
                        npc.getName(),
                        npc.getCombatLevel(),
                        location.getX(),
                        location.getY(),
                        location.getPlane(),
                        client.getWorld());
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
