package com.stefensharkey.osrsxpstatistics;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class XpStatisticsPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(XpStatisticsPlugin.class);
        RuneLite.main(args);
    }
}