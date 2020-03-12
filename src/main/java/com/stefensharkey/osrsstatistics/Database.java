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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class Database {

    private String url;
    private String tableNameKills;
    private String tableNameXp;

    private final Supplier<Stream<String>> SKILLS = () -> Stream.of(Arrays.copyOf(Skill.values(), Skill.values().length - 1))
            .map(Skill::getName)
            .map(String::toLowerCase);

    Database(StatisticsConfig config) {
        updateConfig(config);
    }

    private void createDatabase() {
        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String skills = SKILLS.get().collect(Collectors.joining(" INT UNSIGNED NOT NULL, "));

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
                SKILLS.get().collect(Collectors.joining(", ")) + ", " +
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

    LinkedHashMap<WorldPoint, Double> retrieveXpCountMap(String username, boolean normalized, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        LinkedHashMap<WorldPoint, Double> map = new LinkedHashMap<>();
        double[] max = {0.0F};

        try {
            while (results.next()) {
                int xCoord = results.getInt("x_coord");
                int yCoord = results.getInt("y_coord");
                int plane = results.getInt("plane");
                WorldPoint point = modifiedPoints ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane) : new WorldPoint(xCoord, yCoord, plane);
                double sum = map.getOrDefault(point, 0.0) + 1.0;

                if (sum > max[0]) {
                    max[0] = sum;
                }

                map.put(point, sum);
            }

            if (normalized && max[0] > 0) {
                map.replaceAll((point, sum) -> sum / max[0]);
            }

            return map;
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    LinkedHashMap<WorldPoint, EnumMap<Skill, Double>> retrieveXpTotalMap(String username, boolean normalized, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        LinkedHashMap<WorldPoint, EnumMap<Skill, Double>> map = new LinkedHashMap<>();
        double[] max = {0};

        try {
            while (results.next()) {
                int xCoord = results.getInt("x_coord");
                int yCoord = results.getInt("y_coord");
                int plane = results.getInt("plane");
                WorldPoint point = modifiedPoints ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane) : new WorldPoint(xCoord, yCoord, plane);
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

            if (normalized && max[0] > 0) {
                map.forEach((point, skillMap) -> skillMap.replaceAll((skill, value) -> value / max[0]));
            }

            return map;
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    LinkedHashMap<WorldPoint, EnumMap<Skill, Integer[]>> retrieveXpDeltaMap(String username, boolean modifiedPoints) {
        ResultSet results = retrieveXp(username);
        LinkedHashMap<WorldPoint, EnumMap<Skill, Integer[]>> map = new LinkedHashMap<>();

        try {
            while (results.next()) {
                int xCoord = results.getInt("x_coord");
                int yCoord = results.getInt("y_coord");
                int plane = results.getInt("plane");
                WorldPoint point = modifiedPoints ? new WorldPoint(getModifiedX(xCoord), getModifiedY(yCoord), plane) : new WorldPoint(xCoord, yCoord, plane);
                EnumMap<Skill, Integer[]> skillXpMap = new EnumMap<>(Skill.class);

                SKILLS.get().collect(Collectors.toSet()).forEach(skillName -> {
                    try {
                        Skill skill = Skill.valueOf(skillName.toUpperCase());
                        Integer[] xpValues = new Integer[2];
                        // xpValues[0] is total XP gained on tile
                        // xpValues[1] is number of times XP gained on tile

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
                            IntStream.range(0, map.get(point).get(skill).length).forEach(x -> xpValues[x] += map.get(point).get(skill)[x]);
                        }

                        skillXpMap.put(skill, xpValues);
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
