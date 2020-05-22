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

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.game.ItemStack;
import org.mariadb.jdbc.MariaDbDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
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

    private DataSource dataSource;
    private Connection connection;

    private String tableNameKills;
    private String tableNameLoot;
    private String tableNameXp;

    Database(StatisticsConfig config) {
        updateConfig(config);
    }

    @SneakyThrows
    private synchronized void createDatabase() {
        establishConnection(false);

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
    synchronized void insertKill(String username, int npcId, WorldPoint location, int world) {
        ResultSet resultSet;

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM kills" +
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
    synchronized void insertXp(Actor player, Skill skill, int delta, int world) {
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
            resultSet.updateInt(skill.getName().toLowerCase(), resultSet.getInt(skill.getName()) + delta);
            resultSet.updateInt(skill.getName().toLowerCase() + "_num", resultSet.getInt(skill.getName() + "_num") + 1);
            resultSet.updateRow();
        } else {
            resultSet.moveToInsertRow();

            resultSet.updateString("username", name);
            resultSet.updateInt("x_coord", location.getX());
            resultSet.updateInt("y_coord", location.getY());
            resultSet.updateInt("plane", location.getPlane());
            resultSet.updateInt("world", world);
            resultSet.updateInt(skill.getName().toLowerCase(), delta);
            resultSet.updateInt(skill.getName().toLowerCase() + "_num", 1);

            resultSet.insertRow();
        }
    }

    @SneakyThrows
    synchronized void insertLoot(String username, int npcId, ItemStack itemStack) {
        ResultSet resultSet;

        establishConnection(false);

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

    ResultSet retrieveKill(String username) {
        return retrieve(tableNameKills, username);
    }

    ResultSet retrieveLoot(String username) {
        return retrieve(tableNameLoot, username);
    }

    private synchronized ResultSet retrieve(String tableName, String username) {
        try (PreparedStatement preparedStatement =
                     connection.prepareStatement("SELECT * FROM " + tableName + " WHERE username = ?")) {
            preparedStatement.setString(1, username);
            return preparedStatement.executeQuery();
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }

        return null;
    }

    @SneakyThrows
    Map<WorldPoint, HashMap<Integer, Integer>> retrieveKillMap(String username, boolean modifiedPoints) {
        ResultSet results = retrieveKill(username);
        Map<WorldPoint, HashMap<Integer, Integer>> outerMap = new LinkedHashMap<>();

        while (results.next()) {
            int xCoord = results.getInt("x_coord");
            int yCoord = results.getInt("y_coord");
            int plane = results.getInt("plane");
            WorldPoint point = modifiedPoints
                    ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane)
                    : new WorldPoint(xCoord, yCoord, plane);
            int npcId = results.getInt("npc_id");
            int count = results.getInt("count");
            HashMap<Integer, Integer> innerMap = new HashMap<>();

            innerMap.put(npcId, count);
            outerMap.put(point, innerMap);
        }

        return outerMap;
    }

    @SneakyThrows
    Map<WorldPoint, EnumMap<Skill, Integer[]>> retrieveXpMap(Client client, boolean modifiedPoints) {
        ResultSet resultSet;

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + tableNameXp +
                             " WHERE username = ? AND x_coord >= ? AND x_coord <= ? AND y_coord >= ? AND y_coord <= ? AND plane = ?")) {
            int index = 0;
            WorldPoint point = client.getLocalPlayer().getWorldLocation();

            preparedStatement.setString(++index, client.getLocalPlayer().getName());
            preparedStatement.setInt(++index, point.getX() - 45);
            preparedStatement.setInt(++index, point.getX() + 45);
            preparedStatement.setInt(++index, point.getY() - 45);
            preparedStatement.setInt(++index, point.getY() + 45);
            preparedStatement.setInt(++index, point.getPlane());
            resultSet = preparedStatement.executeQuery();

            Map<WorldPoint, EnumMap<Skill, Integer[]>> map = new LinkedHashMap<>();

            while (resultSet.next()) {
                int xCoord = resultSet.getInt("x_coord");
                int yCoord = resultSet.getInt("y_coord");
                int plane = resultSet.getInt("plane");

                point = new WorldPoint(modifiedPoints ? getModifiedX(xCoord) : xCoord,
                        modifiedPoints ? getModifiedY(yCoord) : yCoord, plane);

                EnumMap<Skill, Integer[]> skillXpMap = new EnumMap<>(Skill.class);

                for (int x = 0; x < Skill.values().length - 1; x++) {
                    String skillName = Skill.values()[x].getName();

                    skillXpMap.put(Skill.valueOf(skillName.replace("_num", "").toUpperCase()),
                            new Integer[] {resultSet.getInt(skillName), resultSet.getInt(skillName + "_num")});
                }

                map.put(point, skillXpMap);
            }

            return map;
        }
    }

    private int getModifiedX (int x) {
        return (x - 1152) * 4;
    }

    private int getModifiedY (int y) {
        return (y - 1216) * 4;
    }


    @SuppressWarnings("HardcodedFileSeparator")
    @SneakyThrows
    synchronized void updateConfig(StatisticsConfig config) {
        dataSource = switch (config.databaseType()) {
            case SQLITE -> {
                SQLiteDataSource tmpDataSource = new SQLiteDataSource();
                tmpDataSource.setUrl("jdbc:sqlite:" + Path.of(RuneLite.RUNELITE_DIR.getAbsolutePath(), "heatmap").toString());
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
        };

        establishConnection(true);

        tableNameKills = config.databaseTablePrefix() + "kills";
        tableNameLoot = config.databaseTablePrefix() + "loot";
        tableNameXp = config.databaseTablePrefix() + "experience";

        createDatabase();
    }

    @SneakyThrows
    private void establishConnection(boolean shouldForce) {
        if (shouldForce) {
            connection = dataSource.getConnection();
        } else {
            boolean shouldOpen = false;

            try {
                if (connection.isClosed()) {
                    shouldOpen = true;
                }
            } catch (SQLException e) {
                shouldOpen = true;
            }

            if (shouldOpen) {
                connection = dataSource.getConnection();
            }
        }
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
