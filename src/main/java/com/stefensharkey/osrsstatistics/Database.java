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

import com.mysql.cj.jdbc.MysqlDataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.game.ItemStack;
import org.mariadb.jdbc.MariaDbDataSource;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class Database {

    private Connection connection;

    private String tableNameKills;
    private String tableNameLoot;
    private String tableNameXp;

    Database(StatisticsConfig config) {
        updateConfig(config);
    }

    @SneakyThrows
    private synchronized void createDatabase() {
        Collection<String> skills = new ArrayList<>((Skill.values().length - 1) << 1);
        for (Skill skill : Arrays.copyOf(Skill.values(), Skill.values().length - 1)) {
            String s = skill.getName().toLowerCase();
            skills.add(s);
            skills.add(s + "_num");
        }

        // XP Table
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + tableNameXp +
                                             """
                                             (
                                                 username VARCHAR(50) NOT NULL,
                                                 x_coord MEDIUMINT UNSIGNED NOT NULL,
                                                 y_coord MEDIUMINT UNSIGNED NOT NULL,
                                                 plane TINYINT UNSIGNED NOT NULL,
                                                 world SMALLINT UNSIGNED NOT NULL,
                                             """ +
                                             String.join(" INT UNSIGNED NOT NULL DEFAULT 0, ", skills) +
                                             " INT UNSIGNED NOT NULL DEFAULT 0, " +
                                             """
                                                 PRIMARY KEY (username, x_coord, y_coord, plane, world)
                                             )
                                             """);

        // Kill Table
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + tableNameKills +
                                             """
                                             (
                                                 username VARCHAR(50) NOT NULL,
                                                 x_coord MEDIUMINT NOT NULL,
                                                 y_coord MEDIUMINT NOT NULL,
                                                 plane TINYINT NOT NULL,
                                                 world SMALLINT UNSIGNED NOT NULL,
                                                 npc_id INT UNSIGNED NOT NULL,
                                                 count INT UNSIGNED NOT NULL DEFAULT 0,
                                                 PRIMARY KEY (username, x_coord, y_coord, plane, world, npc_id)
                                             )
                                             """);

        // Loot Table
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + tableNameLoot +
                                             """
                                             (
                                                 username VARCHAR(320) NOT NULL,
                                                 npc_id BIGINT UNSIGNED NOT NULL,
                                                 item_id INT UNSIGNED NOT NULL,
                                                 quantity INT UNSIGNED NOT NULL,
                                                 PRIMARY KEY (username, npc_id, item_id)
                                             )
                                             """);
    }

    @SneakyThrows
    synchronized void insertKill(Client client, int npcId) {
        Player player = client.getLocalPlayer();
        String username = player.getName();
        WorldPoint location = player.getWorldLocation();
        int world = client.getWorld();

        ResultSet resultSet;

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM " + tableNameKills +
                " WHERE username = ? AND x_coord = ? AND y_coord = ? AND plane = ? AND world = ? AND npc_id = ?",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            int index = 0;

            preparedStatement.setString(++index, username);
            preparedStatement.setInt(++index, location.getX());
            preparedStatement.setInt(++index, location.getY());
            preparedStatement.setInt(++index, location.getPlane());
            preparedStatement.setInt(++index, world);
            preparedStatement.setInt(++index, npcId);

            resultSet = preparedStatement.executeQuery();
        }

        if (resultSet.next()) {
            resultSet.updateInt("count", resultSet.getInt("count") + 1);
            resultSet.updateRow();
        } else {
            resultSet.moveToInsertRow();

            resultSet.updateString("username", username);
            resultSet.updateInt("x_coord", location.getX());
            resultSet.updateInt("y_coord", location.getY());
            resultSet.updateInt("plane", location.getPlane());
            resultSet.updateInt("world", world);
            resultSet.updateInt("npc_id", npcId);
            resultSet.updateInt("count", 1);

            resultSet.insertRow();
        }
    }

    @SneakyThrows
    synchronized void insertXp(Client client, String skillName, int delta) {
        Player player = client.getLocalPlayer();
        String name = player.getName();
        WorldPoint location = player.getWorldLocation();
        String selectSql = "SELECT * FROM " + tableNameXp + " WHERE username = ? AND x_coord = ? AND y_coord = ? AND plane = ?";
        ResultSet resultSet;

        try (PreparedStatement preparedStatement = connection.prepareStatement(selectSql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            int index = 0;

            preparedStatement.setString(++index, name);
            preparedStatement.setInt(++index, location.getX());
            preparedStatement.setInt(++index, location.getY());
            preparedStatement.setInt(++index, location.getPlane());

            resultSet = preparedStatement.executeQuery();
        }

        if (resultSet.next()) {
            resultSet.updateInt(skillName.toLowerCase(), resultSet.getInt(skillName) + delta);
            resultSet.updateInt(skillName.toLowerCase() + "_num", resultSet.getInt(skillName + "_num") + 1);
            resultSet.updateRow();
        } else {
            resultSet.moveToInsertRow();

            resultSet.updateString("username", name);
            resultSet.updateInt("x_coord", location.getX());
            resultSet.updateInt("y_coord", location.getY());
            resultSet.updateInt("plane", location.getPlane());
            resultSet.updateInt("world", client.getWorld());
            resultSet.updateInt(skillName.toLowerCase(), delta);
            resultSet.updateInt(skillName.toLowerCase() + "_num", 1);

            resultSet.insertRow();
        }
    }

    @SneakyThrows
    synchronized void insertLoot(String username, int npcId, ItemStack itemStack) {
        ResultSet resultSet;

        try (PreparedStatement selectStatement = connection.prepareStatement(
                "SELECT * FROM " + tableNameLoot + " WHERE username = ? AND npc_id = ? AND item_id = ?",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            int index = 0;

            selectStatement.setString(++index, username);
            selectStatement.setInt(++index, npcId);
            selectStatement.setInt(++index, itemStack.getId());

            resultSet = selectStatement.executeQuery();
        }

        if (resultSet.next()) {
            resultSet.updateInt("quantity", resultSet.getInt("quantity") + itemStack.getQuantity());
            resultSet.updateRow();
        } else {
            resultSet.moveToInsertRow();

            resultSet.updateString("username", username);
            resultSet.updateInt("npc_id", npcId);
            resultSet.updateInt("item_id", itemStack.getId());
            resultSet.updateInt("quantity", itemStack.getQuantity());

            resultSet.insertRow();
        }
    }

    @SneakyThrows
    Map<WorldPoint, Map<Integer, Integer>> retrieveKillMap(Actor player, boolean nearby) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM " + tableNameKills + " WHERE username = ? AND plane = ?" +
                (nearby ? " AND x_coord >= ? AND x_coord <= ? AND y_coord >= ? AND y_coord <= ?"
                        : ""))) {
            int index = 0;
            WorldPoint point = player.getWorldLocation();

            preparedStatement.setString(++index, player.getName());
            preparedStatement.setInt(++index, point.getPlane());

            if (nearby) {
                preparedStatement.setInt(++index, point.getX() - 45);
                preparedStatement.setInt(++index, point.getX() + 45);
                preparedStatement.setInt(++index, point.getY() - 45);
                preparedStatement.setInt(++index, point.getY() + 45);
            }

            ResultSet resultSet = preparedStatement.executeQuery();

            Map<WorldPoint, Map<Integer, Integer>> outerMap = new HashMap<>();

            while (resultSet.next()) {
                int xCoord = resultSet.getInt("x_coord");
                int yCoord = resultSet.getInt("y_coord");
                int plane = resultSet.getInt("plane");
                point = new WorldPoint(xCoord, yCoord, plane);
                int npcId = resultSet.getInt("npc_id");
                int count = resultSet.getInt("count");
                Map<Integer, Integer> innerMap = new HashMap<>();

                innerMap.put(npcId, count);
                outerMap.put(point, innerMap);
            }

            return outerMap;
        }
    }

    @SneakyThrows
    Map<Integer, Map<Integer, Integer>> retrieveLootMap(String username) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + tableNameLoot + " WHERE username = ?")) {
            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();

            Map<Integer, Map<Integer, Integer>> map = new HashMap<>();

            while (resultSet.next()) {
                int npcId = resultSet.getInt("npc_id");
                int itemId = resultSet.getInt("item_id");
                int quantity = resultSet.getInt("quantity");

                Map<Integer, Integer> innerMap = new HashMap<>();

                innerMap.put(itemId, quantity);
                map.put(npcId, innerMap);
            }

            return map;
        }
    }

    @SneakyThrows
    Map<WorldPoint, EnumMap<Skill, Integer[]>> retrieveXpMap(Actor player) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + tableNameXp +
                             " WHERE username = ? AND x_coord >= ? AND x_coord <= ? AND y_coord >= ? AND y_coord <= ? AND plane = ?")) {
            int index = 0;
            WorldPoint point = player.getWorldLocation();

            preparedStatement.setString(++index, player.getName());
            preparedStatement.setInt(++index, point.getX() - 45);
            preparedStatement.setInt(++index, point.getX() + 45);
            preparedStatement.setInt(++index, point.getY() - 45);
            preparedStatement.setInt(++index, point.getY() + 45);
            preparedStatement.setInt(++index, point.getPlane());

            ResultSet resultSet = preparedStatement.executeQuery();

            Map<WorldPoint, EnumMap<Skill, Integer[]>> map = new HashMap<>();

            while (resultSet.next()) {
                int xCoord = resultSet.getInt("x_coord");
                int yCoord = resultSet.getInt("y_coord");
                int plane = resultSet.getInt("plane");

                point = new WorldPoint(xCoord, yCoord, plane);

                EnumMap<Skill, Integer[]> skillXpMap = new EnumMap<>(Skill.class);

                // For every skill except for Skill.OVERALL, fill the inner map with XP data from resultSet.
                for (int x = 0; x < Skill.values().length - 1; x++) {
                    String skillName = Skill.values()[x].getName();

                    skillXpMap.put(Skill.values()[x],
                            new Integer[] {resultSet.getInt(skillName), resultSet.getInt(skillName + "_num")});
                }

                map.put(point, skillXpMap);
            }

            return map;
        }
    }

    @SneakyThrows
    synchronized void updateConfig(StatisticsConfig config) {
        if (connection != null) {
            connection.close();
        }

        connection = (switch (config.databaseType()) {
            case SQLITE -> {
                SQLiteDataSource tmpDataSource = new SQLiteDataSource();
                tmpDataSource.setUrl("jdbc:sqlite:" + Path.of(RuneLite.RUNELITE_DIR.getAbsolutePath(), "heatmap"));
                tmpDataSource.setDatabaseName(config.databaseName());

                yield tmpDataSource;
            }
            case MYSQL -> {
                MysqlDataSource tmpDataSource = new MysqlDataSource();
                tmpDataSource.setServerName(config.databaseServerIp());
                tmpDataSource.setDatabaseName(config.databaseName());
                tmpDataSource.setUser(config.databaseUsername());
                tmpDataSource.setPassword(config.databasePassword());

                yield tmpDataSource;
            }
            case MARIADB -> {
                MariaDbDataSource tmpDataSource = new MariaDbDataSource();
                tmpDataSource.setServerName(config.databaseServerIp());
                tmpDataSource.setDatabaseName(config.databaseName());
                tmpDataSource.setUser(config.databaseUsername());
                tmpDataSource.setPassword(config.databasePassword());

                yield tmpDataSource;
            }
        }).getConnection();

        tableNameKills = config.databaseTablePrefix() + "kills";
        tableNameLoot = config.databaseTablePrefix() + "loot";
        tableNameXp = config.databaseTablePrefix() + "experience";

        createDatabase();
    }

    public enum DatabaseType {

        SQLITE("sqlite"),
        MYSQL("mysql"),
        MARIADB("mariadb");

        private final String name;

        DatabaseType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
