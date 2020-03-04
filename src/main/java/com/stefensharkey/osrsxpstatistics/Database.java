package com.stefensharkey.osrsxpstatistics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
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

    private static XpStatisticsConfig config;

    private final String URL = String.format("jdbc:%s://%s/%s?user=%s&password=%s",
                                        config.databaseType().getName(),
                                        config.databaseServerIp(),
                                        config.databaseName(),
                                        config.databaseUsername(),
                                        config.databasePassword());

    private final String tableName = config.databaseTablePrefix() + config.databaseTableName();

    private Database() {

    }

    public void createDatabase() {
        try (Connection connection = DriverManager.getConnection(URL)) {
            if (connection != null) {
                String skills = Stream.of(Arrays.copyOf(Skill.values(), Skill.values().length - 1))
                        .map(Skill::getName)
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(" INT UNSIGNED NOT NULL, "));

                connection.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                                "id INT NOT NULL AUTO_INCREMENT, " +
                                "username VARCHAR(320) NOT NULL, " +
                                "xp_datetime DATETIME(3) NOT NULL, " +
                                skills + " INT UNSIGNED NOT NULL, " +
                                "x_coord MEDIUMINT NOT NULL, " +
                                "y_coord MEDIUMINT NOT NULL, " +
                                "plane TINYINT NOT NULL, " +
                                "world SMALLINT UNSIGNED NOT NULL)");
            }
        } catch (SQLException e) {
            log.error(e.getLocalizedMessage());
        }
    }

    public void insertKill(String username, Timestamp dateTime, NPC npc, int xCoord, int yCoord, int plane, int world) {
        log.info(npc.toString());
    }

    public void insertXp(String username, Timestamp dateTime, LinkedHashMap<Skill, Integer> skills, int xCoord, int yCoord, int plane, int world) {
        String sql = "INSERT INTO " + tableName +
                " (username, xp_datetime, " +
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
        try (Connection connection = DriverManager.getConnection(URL);
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public ResultSet retrieve(String username) {
        try (Connection connection = DriverManager.getConnection(URL)) {
            return connection.createStatement().executeQuery("SELECT * FROM " + tableName +
                                                                  " WHERE username = '" + username + "'");
        } catch (SQLException e) {
            log.error(e.getMessage());
        }

        return null;
    }

    // Inner class to provide instance of class
    private static class DatabaseSingleton {
        private static final Database INSTANCE = new Database();
    }

    public static Database getInstance(XpStatisticsConfig config) {
        Database.config = config;

        return DatabaseSingleton.INSTANCE;
    }

    public static Database getInstance() {
        assert Database.config != null;
        return DatabaseSingleton.INSTANCE;
    }
}
