package com.stefensharkey.osrsstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class Database {

    private String url;
    private String tableNameKills;
    private String tableNameXp;

    Stream<String> skillStream = Stream.of(Arrays.copyOf(Skill.values(), Skill.values().length - 1))
            .map(Skill::getName)
            .map(String::toLowerCase);

    Database(StatisticsConfig config) {
        updateConfig(config);
    }

    private void createDatabase() {
        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String skills = skillStream.collect(Collectors.joining(" INT UNSIGNED NOT NULL, "));

                connection.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS " + tableNameXp + " (" +
                                "id INT NOT NULL AUTO_INCREMENT, " +
                                "username VARCHAR(320) NOT NULL, " +
                                "update_time DATETIME(3) NOT NULL, " +
                                skills + " INT UNSIGNED NOT NULL, " +
                                "x_coord MEDIUMINT NOT NULL, " +
                                "y_coord MEDIUMINT NOT NULL, " +
                                "plane TINYINT NOT NULL, " +
                                "world SMALLINT UNSIGNED NOT NULL, " +
                                "PRIMARY KEY (id))");

                connection.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS " + tableNameKills + " (" +
                                "id INT NOT NULL AUTO_INCREMENT, " +
                                "username VARCHAR(320) NOT NULL, " +
                                "update_time DATETIME(3) NOT NULL, " +
                                "npc_name VARCHAR(255) NOT NULL, " +
                                "npc_level MEDIUMINT UNSIGNED NOT NULL, " +
                                "x_coord MEDIUMINT NOT NULL, " +
                                "y_coord MEDIUMINT NOT NULL, " +
                                "plane TINYINT NOT NULL, " +
                                "world SMALLINT UNSIGNED NOT NULL, " +
                                "PRIMARY KEY (id))");
            }
        } catch (SQLException e) {
            log.error(e.getLocalizedMessage());
        }
    }

    void insertKill(String username, Timestamp dateTime, String npcName, int npcLevel, int xCoord, int yCoord, int plane, int world) {
        String sql = "INSERT INTO " + tableNameKills +
                " (username, update_time, npc_name, npc_level, x_coord, y_coord, plane, world) " +
                "VALUES ('" + username + "', " +
                "'" + dateTime + "', " +
                "'" + npcName + "', " +
                npcLevel + ", " +
                xCoord + ", " +
                yCoord + ", " +
                plane + ", " +
                world + ")";

        insert(sql);
    }

    void insertXp(String username, Timestamp dateTime, Map<Skill, Integer> skills, int xCoord, int yCoord, int plane, int world) {
        String sql = "INSERT INTO " + tableNameXp +
                " (username, update_time, " +
                skillStream.collect(Collectors.joining(", ")) + ", " +
                "x_coord, y_coord, plane, world) " +
                "VALUES ('" + username + "', " +
                "'" + dateTime + "', " +
                skills.values().stream().map(String::valueOf).collect(Collectors.joining(", ")) + ", " +
                xCoord + ", " +
                yCoord + ", " +
                plane + ", " +
                world + ")";

        insert(sql);
    }

    private void insert(String sql) {
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    ResultSet retrieveKill(String username) {
        return retrieve("SELECT * FROM " + tableNameKills + " WHERE username = '" + username + "'");
    }

    ResultSet retrieveXp(String username) {
        return retrieve("SELECT * FROM " + tableNameXp + " WHERE username = '" + username + "'");
    }

    HashMap<WorldPoint, HashMap<Skill, Integer>> retrieveXpMap(String username, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        HashMap<WorldPoint, HashMap<Skill, Integer>> map = new HashMap<>();

        try {
            while (results.next()) {
                int x = results.getInt("x_coord");
                int y = results.getInt("y_coord");
                int plane = results.getInt("plane");
                WorldPoint point = modifiedPoints ? new WorldPoint(getModifiedX(x), getModifiedY(y), plane) : new WorldPoint(x, y, plane);
                HashMap<Skill, Integer> skillXpMap = new HashMap<>();

                skillStream.forEach(skillName -> {
                    try {
                        skillXpMap.put(Skill.valueOf(skillName), results.getInt(skillName));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });

                map.put(point, skillXpMap);
            }

            return map;
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    HashMap<WorldPoint, Integer> retrieveXpCountMap(String username, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        HashMap<WorldPoint, Integer> map = new HashMap<>();

        try {
            while (results.next()) {
                int x = results.getInt("x_coord");
                int y = results.getInt("y_coord");
                int plane = results.getInt("plane");
                WorldPoint point = modifiedPoints ? new WorldPoint(getModifiedX(x), getModifiedY(y), plane) : new WorldPoint(x, y, plane);
                int count = map.get(point) == null ? 1 : map.get(point) + 1;

                log.info("{point.x={}, point.y={}, point.plane={}", point.getX(), point.getY(), point.getPlane());

                map.put(point, count);
            }

            return map;
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    HashMap<WorldPoint, Float> retrieveXpCountWeightedMap(String username, boolean modifiedPoints) {
        HashMap<WorldPoint, Integer> originalMap = retrieveXpCountMap(username, modifiedPoints);
        HashMap<WorldPoint, Float> newMap = new HashMap<>();
        originalMap.forEach((key, value) -> newMap.put(key, (float) value / originalMap.size()));
        return newMap;
    }


    private int getModifiedX (int x) {
        return (x - 1152) * 4;
    }

    private int getModifiedY (int y) {
        return (y - 1216) * 4;
    }

    private ResultSet retrieve(String sql) {
        try (Connection connection = DriverManager.getConnection(url)) {
            return connection.createStatement().executeQuery(sql);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }

        return null;
    }


    @SuppressWarnings("HardcodedFileSeparator")
    void updateConfig(StatisticsConfig config) {
        url = String.format("jdbc:%s://%s/%s?user=%s&password=%s",
                config.databaseType().getName(),
                config.databaseServerIp(),
                config.databaseName(),
                config.databaseUsername(),
                config.databasePassword());

        tableNameKills = config.databaseTablePrefix() + "kills";
        tableNameXp = config.databaseTablePrefix() + "experience";

        createDatabase();
    }
}
