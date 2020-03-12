package com.stefensharkey.osrsstatistics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@SuppressWarnings("SameReturnValue")
@ConfigGroup("statistics")
public interface StatisticsConfig extends Config {

    @ConfigItem(
            keyName = "databasetype",
            name = "Database Type",
            description = "Type of the database server.",
            position = 0
    )
    default DatabaseType databaseType() {
        return DatabaseType.MARIADB;
    }

    @ConfigItem(
            keyName = "databaseserverip",
            name = "Database Server IP",
            description = "IP(:port) of the database server.",
            position = 1
    )
    default String databaseServerIp() {
        return "67.246.243.177";
    }

    @ConfigItem(
            keyName = "databaseusername",
            name = "Database Username",
            description = "Username for the the database server.",
            position = 2
    )
    default String databaseUsername() {
        return "osrsstatistics";
    }

    @ConfigItem(
            keyName = "databasepassword",
            name = "Database Password",
            description = "Password for the the database server.",
            secret = true,
            position = 3
    )
    default String databasePassword() {
        return "";
    }

    @ConfigItem(
            keyName = "databasename",
            name = "Database Name",
            description = "Name of the database on the server.",
            position = 4
    )
    default String databaseName() {
        return "osrs_statistics";
    }

    @ConfigItem(
            keyName = "databasetableprefix",
            name = "Database Table Prefix",
            description = "Prefix for the table in the database.",
            position = 5
    )
    default String databaseTablePrefix() {
        return "";
    }

    @ConfigItem(
            keyName = "heatmapdotsize",
            name = "Heat Map Dot Size",
            description = "How large the dots on the heat map should be.",
            hidden = true,
            position = 6
    )
    default int heatMapDotSize() {
        return 25;
    }

    @ConfigItem(
            keyName = "displayxptileoverlay",
            name = "Overlay Enabled",
            description = "Display overlay over tiles XP was received on.",
            position = 7
    )
    default boolean displayXpTileOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "displayxptotal",
            name = "Overlay XP Total",
            description = "Display XP total per tile instead of number of times XP gained.",
            position = 8
    )
    default boolean displayXpTotal() {
        return true;
    }
}
