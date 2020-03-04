package com.stefensharkey.osrsxpstatistics;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
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

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;

@Slf4j
@PluginDescriptor(
        name = "XP Statistics"
)
public class XpStatisticsPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    protected XpStatisticsConfig config;

    private final LinkedHashMap<Skill, Integer> skillXpCache = new LinkedHashMap<>();

    // Necessary for the initial Xp loop.
//    private int counter = 0;

    private Database database;

    @Override
    protected void startUp() {
        log.info("Xp Statistics started!");
        database = Database.getInstance(config);
        database.createDatabase();
    }

    @Override
    protected void shutDown() {
        log.info("Xp Statistics stopped!");
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        Skill skill = statChanged.getSkill();
        int xp = statChanged.getXp();
        log.info("onStatChanged() fired for {}", skill.getName());

        // We don't care about the overall skill or experience, as it can be calculated.
        if (skill != Skill.OVERALL) {
            // When a player logs in, this method is fired for each skill. We can use this to populate the cache.
            if (skillXpCache.size() < (Skill.values().length - 1)) {
                log.info("XP starting up.");
                skillXpCache.put(skill, xp);
                log.info(skillXpCache.toString());
//                counter++;
            } else {
                // If the skill XP cache doesn't exist, create it.
//                if (skillXpCache == null) {
//                    log.info("XP cache initializing.");
//                    initXpCache();
//                }

                // Occasionally, this method will be fired undesirably. To counteract this, only proceed if there is
                // actually an XP change.
                if (skillXpCache.get(skill) != xp) {
                    log.info("XP change: {oldXP={}, newXP={}}", skillXpCache.get(skill), xp);

                    skillXpCache.put(skill, xp);
                    Player player = client.getLocalPlayer();

                    // Sanity check for player nullability.
                    if (player != null) {
                        log.info("Inserting into database.");

                        WorldPoint location = player.getWorldLocation();

                        database.insertXp(player.getName(),
                                        new Timestamp(new Date().getTime()),
                                        skillXpCache,
                                        location.getX(),
                                        location.getY(),
                                        location.getPlane(),
                                        client.getWorld());
                    } else {
                        log.error("Player does not exist.");
                    }
                } else {
                    log.warn("Cached XP is the same as current XP.");
                }
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        /* In order to accurately maintain XP cache, we must reset the cache only as the user is logging in.
           If we use GameState.LOGGED_IN instead we would occasionally reset the cache without re-initializing it. This
           is because, when the user does certain actions such as loading into a new region, this method is fired for
           GameState.LOGGED_IN, without firing GameStatChanged event for all skills. */
        if (gameStateChanged.getGameState() == GameState.LOGGING_IN) {
            log.info("Resetting XP cache.");
            clearXpCache();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        NPC npc = npcDespawned.getNpc();

        if (npc.isDead()) {
            Player player = client.getLocalPlayer();
//            database.insertKill();
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
                database.createDatabase();
                break;
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted commandExecuted) {
        log.info(commandExecuted.getCommand());

        switch (commandExecuted.getCommand().toLowerCase()) {
            case "xpheat":
                XpHeatMap heatMap = new XpHeatMap(config);

                try {
                    heatMap.generateHeatMap();
                } catch (IOException | SQLException e) {
                    log.error(e.getLocalizedMessage());
                }

                break;
        }
    }

    @Provides
    XpStatisticsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(XpStatisticsConfig.class);
    }

//    private void initXpCache() {
//        /* We are not interested in Skill.OVERALL, as it can be calculated post-capture. Conveniently, it is the last
//           enum in Skill. */
//        int newLength = Skill.values().length - 1;
//        Skill[] skills = Arrays.copyOf(Skill.values(), newLength);
//        int[] skillExperiences = Arrays.copyOf(client.getSkillExperiences(), newLength);
//
//        skillXpCache = IntStream.range(0, skills.length).boxed()
//                .collect(Collectors.toMap(
//                        x -> skills[x],
//                        y -> skillExperiences[y],
//                        (x, y) -> {
//                            throw new IllegalStateException(String.format("Duplicate key %s", x));
//                        },
//                        LinkedHashMap::new));
//    }

    private void clearXpCache() {
        skillXpCache.clear();
//        counter = 0;
    }
}
