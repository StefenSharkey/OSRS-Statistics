package com.stefensharkey.osrsxpstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class Database {

    private XpStatisticsConfig config;

    private String url;
    private String tableNameKills;
    private String tableNameXp;

    public Database(XpStatisticsConfig config) {
        updateConfig(config);
    }

    private void createDatabase() {
        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String skills = Stream.of(Arrays.copyOf(Skill.values(), Skill.values().length - 1))
                        .map(Skill::getName)
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(" INT UNSIGNED NOT NULL, "));

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

    public void insertKill(String username, Timestamp dateTime, String npcName, int npcLevel, int xCoord, int yCoord, int plane, int world) {
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

    public void insertXp(String username, Timestamp dateTime, LinkedHashMap<Skill, Integer> skills, int xCoord, int yCoord, int plane, int world) {
        String sql = "INSERT INTO " + tableNameXp +
                " (username, update_time, " +
                skills.keySet().stream()
                        .map(Skill::getName)
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(", ")) + ", " +
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

    public ResultSet retrieveKill(String username) {
        return retrieve("SELECT * FROM " + tableNameKills + " WHERE username = '" + username + "'");
    }

    public ResultSet retrieveXp(String username) {
        return retrieve("SELECT * FROM " + tableNameXp + " WHERE username = '" + username + "'");
    }

    private ResultSet retrieve(String sql) {
        try (Connection connection = DriverManager.getConnection(url)) {
            return connection.createStatement().executeQuery(sql);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }

        return null;
    }


    public void updateConfig(XpStatisticsConfig config) {
        this.config = config;

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
