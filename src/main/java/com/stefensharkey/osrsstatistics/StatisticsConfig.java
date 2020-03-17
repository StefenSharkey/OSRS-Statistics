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

    @ConfigItem(
            keyName = "displaytooltip",
            name = "Tooltip Enabled",
            description = "Display tooltip detailing XP gain per tile (Note: requires overlay enabled).",
            position = 9
    )
    default boolean displayTooltip() {
        return true;
    }

    @ConfigItem(
            keyName = "displaytooltipxptotal",
            name = "XP Total on Tooltip",
            description = "Change skill highlighting to XP total instead of number of times XP gained.",
            position = 10
    )
    default boolean displayTooltipXpTotal() {
        return true;
    }
}
