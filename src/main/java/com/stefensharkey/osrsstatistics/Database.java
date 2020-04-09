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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class Database {

    private DataSource dataSource;
    private Connection connection;

    private String tableNameKills;
    private String tableNameLoot;
    private String tableNameXp;

    private static final Supplier<Stream<String>> SKILLS = () ->
            Stream.of(Arrays.copyOf(Skill.values(), Skill.values().length - 1))
                    .map(Skill::getName)
                    .map(String::toLowerCase);

    Database(StatisticsConfig config) {
        updateConfig(config);
    }

    @SneakyThrows
    private synchronized void createDatabase() {
        String skills = SKILLS.get()
                .collect(Collectors.joining(" INT UNSIGNED NOT NULL, ")) + " INT UNSIGNED NOT NULL, ";

        establishConnection(false);

        // XP Table
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + tableNameXp +
                                             """
                                             (
                                                 id INT NOT NULL AUTO_INCREMENT,
                                                 username VARCHAR(320) NOT NULL,
                                                 update_time DATETIME(3) NOT NULL,
                                             """ + skills + """
                                                 x_coord MEDIUMINT NOT NULL,
                                                 y_coord MEDIUMINT NOT NULL,
                                                 plane TINYINT NOT NULL,
                                                 world SMALLINT UNSIGNED NOT NULL,
                                                 PRIMARY KEY (id)
                                             )
                                             """);

        // Kill Table
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + tableNameKills +
                                             """
                                             (
                                                 id INT NOT NULL AUTO_INCREMENT,
                                                 username VARCHAR(320) NOT NULL,
                                                 update_time DATETIME(3) NOT NULL,
                                                 npc_name VARCHAR(255) NOT NULL,
                                                 npc_level MEDIUMINT UNSIGNED NOT NULL,
                                                 x_coord MEDIUMINT NOT NULL,
                                                 y_coord MEDIUMINT NOT NULL,
                                                 plane TINYINT NOT NULL,
                                                 world SMALLINT UNSIGNED NOT NULL,
                                                 PRIMARY KEY (id)
                                             )
                                             """);

        // Loot Table
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + tableNameLoot +
                                             """
                                             (
                                                 username VARCHAR(320) NOT NULL,
                                                 npc_name VARCHAR(255) NOT NULL,
                                                 npc_level MEDIUMINT UNSIGNED NOT NULL,
                                                 item_id INT NOT NULL,
                                                 quantity INT UNSIGNED NOT NULL,
                                                 PRIMARY KEY (username, npc_name, npc_level, item_id)
                                             )
                                             """);
    }

    synchronized void insertKill(String username, LocalDateTime dateTime, Actor npc, WorldPoint location, int world) {
        String sql = "INSERT INTO " + tableNameKills +
                " (username, update_time, npc_name, npc_level, x_coord, y_coord, plane, world) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        establishConnection(false);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int index = 0;

            preparedStatement.setString(++index, username);
            preparedStatement.setTimestamp(++index, Timestamp.valueOf(dateTime));
            preparedStatement.setString(++index, npc.getName());
            preparedStatement.setInt(++index, npc.getCombatLevel());
            preparedStatement.setInt(++index, location.getX());
            preparedStatement.setInt(++index, location.getY());
            preparedStatement.setInt(++index, location.getPlane());
            preparedStatement.setInt(++index, world);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }
    }

    synchronized void insertXp(String username, LocalDateTime dateTime, Map<Skill, Integer> skills, WorldPoint location, int world) {
        String sql = "INSERT INTO " + tableNameXp +
                     " (username, update_time, " + SKILLS.get().collect(Collectors.joining(", ")) + ", x_coord, y_coord, plane, world) " +
                     "VALUES (?, ?, " + skills.values().stream().map(String::valueOf).collect(Collectors.joining(", ")) + ", ?, ?, ?, ?)";

        establishConnection(false);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int index = 0;

            preparedStatement.setString(++index, username);
            preparedStatement.setTimestamp(++index, Timestamp.valueOf(dateTime));
            preparedStatement.setInt(++index, location.getX());
            preparedStatement.setInt(++index, location.getY());
            preparedStatement.setInt(++index, location.getPlane());
            preparedStatement.setInt(++index, world);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }
    }

    synchronized void insertLoot(String username, Actor npc, ItemStack itemStack) {
        String npcName = npc.getName();
        int npcLevel = npc.getCombatLevel();
        ResultSet resultSet;

        establishConnection(false);

        try {
            try (PreparedStatement selectStatement = connection.prepareStatement(
                    "SELECT * FROM " + tableNameLoot + " WHERE username = ? AND npc_name = ? AND npc_level = ? AND item_id = ?")) {
                int index = 0;

                selectStatement.setString(++index, username);
                selectStatement.setString(++index, npcName);
                selectStatement.setInt(++index, npcLevel);
                selectStatement.setInt(++index, itemStack.getId());

                resultSet = selectStatement.executeQuery();
            }

            boolean exists = resultSet.next();
            try (PreparedStatement updateStatement = connection.prepareStatement(exists
                    ? "UPDATE " + tableNameLoot + " SET quantity = ? WHERE username = ? AND npc_name = ? AND npc_level = ? AND item_id = ?"
                    : "INSERT INTO " + tableNameLoot + " VALUES (?, ?, ?, ?, ?)")) {
                int index = 0;

                if (exists) {
                    updateStatement.setInt(++index, itemStack.getQuantity() + resultSet.getInt("quantity"));
                    updateStatement.setString(++index, username);
                    updateStatement.setString(++index, npcName);
                    updateStatement.setInt(++index, npcLevel);
                    updateStatement.setInt(++index, itemStack.getId());
                } else {
                    updateStatement.setString(++index, username);
                    updateStatement.setString(++index, npcName);
                    updateStatement.setInt(++index, npcLevel);
                    updateStatement.setInt(++index, itemStack.getId());
                    updateStatement.setInt(++index, itemStack.getQuantity());
                }

                updateStatement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }
    }

    ResultSet retrieveKill(String username) {
        return retrieve(tableNameKills, username);
    }

    ResultSet retrieveLoot(String username) {
        return retrieve(tableNameLoot, username);
    }

    ResultSet retrieveXp(String username) {
        return retrieve(tableNameXp, username);
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
    Map<WorldPoint, HashMap<String, Integer>> retrieveKillMap(String username, boolean modifiedPoints) {
        ResultSet results = retrieveKill(username);
        Map<WorldPoint, HashMap<String, Integer>> outerMap = new LinkedHashMap<>();

        while (results.next()) {
            int xCoord = results.getInt("x_coord");
            int yCoord = results.getInt("y_coord");
            int plane = results.getInt("plane");
            WorldPoint point = modifiedPoints
                    ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane)
                    : new WorldPoint(xCoord, yCoord, plane);
            String npcName = results.getString("npc_name");
            int count = 1 + ((outerMap.get(point) != null && outerMap.get(point).get(npcName) != null)
                    ? outerMap.get(point).get(npcName)
                    : 0);
            HashMap<String, Integer> innerMap = new HashMap<>();

            innerMap.put(npcName, count);
            outerMap.put(point, innerMap);
        }

        return outerMap;
    }

    @SneakyThrows
    Map<WorldPoint, Double> retrieveXpCountMap(String username, boolean normalized, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        Map<WorldPoint, Double> map = new LinkedHashMap<>();
        double[] max = {0.0};

        while (results.next()) {
            int xCoord = results.getInt("x_coord");
            int yCoord = results.getInt("y_coord");
            int plane = results.getInt("plane");
            WorldPoint point = modifiedPoints
                    ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane)
                    : new WorldPoint(xCoord, yCoord, plane);
            double sum = map.getOrDefault(point, 0.0) + 1.0;

            if (sum > max[0]) {
                max[0] = sum;
            }

            map.put(point, sum);
        }

        if (normalized && max[0] > 0.0) {
            map.replaceAll((point, sum) -> sum / max[0]);
        }

        return map;
    }

    @SneakyThrows
    Map<WorldPoint, EnumMap<Skill, Double>> retrieveXpTotalMap(String username, boolean normalized, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        Map<WorldPoint, EnumMap<Skill, Double>> map = new LinkedHashMap<>();
        double[] max = {0.0};

        while (results.next()) {
            int xCoord = results.getInt("x_coord");
            int yCoord = results.getInt("y_coord");
            int plane = results.getInt("plane");
            WorldPoint point = modifiedPoints
                    ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane)
                    : new WorldPoint(xCoord, yCoord, plane);
            EnumMap<Skill, Double> skillXpMap = new EnumMap<>(Skill.class);

            SKILLS.get().collect(Collectors.toSet()).forEach(skillName -> {
                try {
                    double xpValue = results.getInt(skillName);

                    if (xpValue > max[0]) {
                        max[0] = xpValue;
                    }

                    skillXpMap.put(Skill.valueOf(skillName.toUpperCase()), xpValue);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });

            map.put(point, skillXpMap);
        }

        if (normalized && max[0] > 0.0) {
            map.forEach((point, skillMap) -> skillMap.replaceAll((skill, value) -> value / max[0]));
        }

        return map;
    }

    @SneakyThrows
    Map<WorldPoint, EnumMap<Skill, Integer[]>> retrieveXpDeltaMap(String username, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        Map<WorldPoint, EnumMap<Skill, Integer[]>> map = new LinkedHashMap<>();

        while (results.next()) {
            int xCoord = results.getInt("x_coord");
            int yCoord = results.getInt("y_coord");
            int plane = results.getInt("plane");
            WorldPoint point = modifiedPoints
                    ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane)
                    : new WorldPoint(xCoord, yCoord, plane);
            EnumMap<Skill, Integer[]> skillXpMap = new EnumMap<>(Skill.class);

            SKILLS.get().collect(Collectors.toSet()).forEach(skillName -> {
                try {
                    Skill skill = Skill.valueOf(skillName.toUpperCase());

                    // xpValues[0] is total XP gained on tile
                    // xpValues[1] is number of times XP gained on tile
                    Integer[] xpValues = new Integer[2];

                    xpValues[0] = results.getInt(skillName);

                    // If a value already existed in the previous row, subtract it from the current value to get the
                    // delta.
                    if (results.previous()) {
                        xpValues[0] -= results.getInt(skillName);
                    }

                    results.next();

                    // If XP was never obtained on that tile, then the count is zero; otherwise, note the delta.
                    xpValues[1] = xpValues[0] > 0 ? 1 : 0;

                    // If the point was already mapped and had a skill mapped to it, .
                    if (map.get(point) != null && map.get(point).get(skill) != null) {
                        IntStream.range(0, map.get(point).get(skill).length).forEach(x ->
                                xpValues[x] += map.get(point).get(skill)[x]
                        );
                    }

                    skillXpMap.put(skill, xpValues);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });

            map.put(point, skillXpMap);
        }

        return map;
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
