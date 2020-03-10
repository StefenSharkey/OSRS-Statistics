package com.stefensharkey.osrsstatistics;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class StatisticsPluginTest
{
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(StatisticsPlugin.class);
        RuneLite.main(args);
    }
}