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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.mariadb.jdbc.MariaDbDataSource;

import javax.sql.DataSource;
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

    private void createDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null) {
                String skills = SKILLS.get()
                        .collect(Collectors.joining(" INT UNSIGNED NOT NULL, ")) + " INT UNSIGNED NOT NULL, ";

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
                connection.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS " + tableNameLoot +
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
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }
    }


    void insertKill(String username, LocalDateTime dateTime, Actor npc, WorldPoint location, int world) {
        String sql =
                "INSERT INTO " + tableNameKills +
                " (username, update_time, npc_name, npc_level, x_coord, y_coord, plane, world) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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

    void insertXp(String username, LocalDateTime dateTime, Map<Skill, Integer> skills, WorldPoint location, int world) {
        String sql = "INSERT INTO " + tableNameXp + " (username, update_time, ?, x_coord, y_coord, plane, world) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int index = 0;
            preparedStatement.setString(++index, SKILLS.get().collect(Collectors.joining(", ")));
            preparedStatement.setString(++index, username);
            preparedStatement.setTimestamp(++index, Timestamp.valueOf(dateTime));
            preparedStatement.setString(++index,
                    skills.values().stream().map(String::valueOf).collect(Collectors.joining(", ")));
            preparedStatement.setInt(++index, location.getX());
            preparedStatement.setInt(++index, location.getY());
            preparedStatement.setInt(++index, location.getPlane());
            preparedStatement.setInt(++index, world);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            log.error("SQL Error", e);
        }
    }

    @SneakyThrows
    void insertLoot(String username, Actor npc, int itemId, int quantity) {
        String npcName = npc.getName();
        int npcLevel = npc.getCombatLevel();
        ResultSet resultSet = null;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement selectStatement = connection.prepareStatement(
                    "SELECT * FROM ? WHERE username = ? AND npc_name = ? AND npc_level = ? AND item_id = ?")) {
                int index = 0;

                selectStatement.setString(++index, tableNameLoot);
                selectStatement.setString(++index, username);
                selectStatement.setString(++index, npcName);
                selectStatement.setInt(++index, npcLevel);
                selectStatement.setInt(++index, itemId);

                resultSet = selectStatement.executeQuery();
            }

            boolean exists = resultSet.next();
            try (PreparedStatement updateStatement = connection.prepareStatement(exists
                    ? "UPDATE ? SET quantity = ? WHERE username = ? AND npc_name = ? AND npc_level = ? AND item_id = ?"
                    : "INSERT INTO ? VALUES (?, ?, ?, ?, ?)")) {
                int index = 0;

                updateStatement.setString(++index, tableNameLoot);

                if (exists) {
                    updateStatement.setInt(++index, quantity + resultSet.getInt("quantity"));
                    updateStatement.setString(++index, username);
                    updateStatement.setString(++index, npcName);
                    updateStatement.setInt(++index, npcLevel);
                    updateStatement.setInt(++index, itemId);
                } else {
                    updateStatement.setString(++index, username);
                    updateStatement.setString(++index, npcName);
                    updateStatement.setInt(++index, npcLevel);
                    updateStatement.setInt(++index, itemId);
                    updateStatement.setInt(++index, quantity);
                }

                updateStatement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("SQL Error", e);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    private void insert(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
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

    private ResultSet retrieve(String tableName, String username) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement =
                     connection.prepareStatement("SELECT * FROM ? WHERE username = ?")) {
            int index = 0;

            preparedStatement.setString(++index, tableName);
            preparedStatement.setString(++index, username);

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
            int count = ((outerMap.get(point) != null && outerMap.get(point).get(npcName) != null)
                    ? outerMap.get(point).get(npcName)
                    : 0) + 1;
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
    void updateConfig(StatisticsConfig config) {
        String url = String.format("jdbc:%s://%s/%s?user=%s&password=%s",
                config.databaseType().getName(),
                config.databaseServerIp(),
                config.databaseName(),
                config.databaseUsername(),
                config.databasePassword());

        if (config.databaseType() == DatabaseType.MYSQL) {
            // TODO: Determine a DataSource object for MySQL.
        } else if (config.databaseType() == DatabaseType.MARIADB) {
            dataSource = new MariaDbDataSource(url);
        }

        tableNameKills = config.databaseTablePrefix() + "kills";
        tableNameLoot = config.databaseTablePrefix() + "loot";
        tableNameXp = config.databaseTablePrefix() + "experience";

        createDatabase();
    }

    public enum DatabaseType {

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
