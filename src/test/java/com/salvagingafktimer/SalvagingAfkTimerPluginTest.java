package com.salvagingafktimer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SalvagingAfkTimerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SalvagingAfkTimerPlugin.class);
		RuneLite.main(args);
	}
}