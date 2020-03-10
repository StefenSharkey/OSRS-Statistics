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
            description = "Type of the database server."
    )
    default DatabaseType databaseType() {
        return DatabaseType.MARIADB;
    }

    @ConfigItem(
            keyName = "databaseserverip",
            name = "Database Server IP",
            description = "IP(:port) of the database server."
    )
    default String databaseServerIp() {
        return "67.246.243.177";
    }

    @ConfigItem(
            keyName = "databaseusername",
            name = "Database Username",
            description = "Username for the the database server."
    )
    default String databaseUsername() {
        return "osrsstatistics";
    }

    @ConfigItem(
            keyName = "databasepassword",
            name = "Database Password",
            description = "Password for the the database server.",
            secret = true
    )
    default String databasePassword() {
        return "";
    }

    @ConfigItem(
            keyName = "databasename",
            name = "Database Name",
            description = "Name of the database on the server."
    )
    default String databaseName() {
        return "osrs_statistics";
    }

    @ConfigItem(
            keyName = "databasetableprefix",
            name = "Database Table Prefix",
            description = "Prefix for the table in the database."
    )
    default String databaseTablePrefix() {
        return "";
    }

    @ConfigItem(
            keyName = "heatmapdotsize",
            name = "Heat Map Dot Size",
            description = "How large the dots on the heat map should be.",
            hidden = true
    )
    default int heatMapDotSize() {
        return 25;
    }
}
