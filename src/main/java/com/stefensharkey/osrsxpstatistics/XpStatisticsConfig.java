package com.stefensharkey.osrsxpstatistics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("xpstatistics")
public interface XpStatisticsConfig extends Config {

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
        return "xpstatistics";
    }

    @ConfigItem(
            keyName = "databasepassword",
            name = "Database Password",
            description = "Password for the the database server."
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
        return "xp_statistics";
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
            keyName = "databasetablename",
            name = "Database Table Name",
            description = "Name for the table in the database."
    )
    default String databaseTableName() {
        return "xp_statistics";
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

    @ConfigItem(
            keyName = "heatmapscale",
            name = "Heat Map Scale",
            description = "How large to scale the coordinates.",
            hidden = true
    )
    default int heatMapScale() {
        return 3;
    }

    @ConfigItem(
            keyName = "heatmapoffsetx",
            name = "Heat Map X Offset",
            description = "How much the coordinates should be horizontally offset.",
            hidden = true
    )
    default int heatMapOffsetX() {
        return 3429;
    }

    @ConfigItem(
            keyName = "heatmapoffsety",
            name = "Heat Map Y Offset",
            description = "How much the coordinates should be vertically offset.",
            hidden = true
    )
    default int heatMapOffsetY() {
        return 7003;
    }
}
