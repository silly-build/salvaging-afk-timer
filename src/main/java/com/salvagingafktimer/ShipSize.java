package com.salvagingafktimer;

import net.runelite.api.gameval.ObjectID;

public enum ShipSize
{
	AUTO("Auto", new int[0]),
	RAFT("Raft", new int[]{
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_WOOD,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_OAK,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_TEAK,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_MAHOGANY,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_CAMPHOR,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_IRONWOOD,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_ROSEWOOD
	}),
	SKIFF("Skiff", new int[]{
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_WOOD,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_OAK,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_TEAK,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_MAHOGANY,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_CAMPHOR,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_IRONWOOD,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_ROSEWOOD
	}),
	SLOOP("Sloop", new int[]{
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_WOOD,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_OAK,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_TEAK,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_MAHOGANY,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_CAMPHOR,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_IRONWOOD,
		ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_ROSEWOOD
	});

	private final String displayName;
	private final int[] gameObjectIds;

	ShipSize(String displayName, int[] gameObjectIds)
	{
		this.displayName = displayName;
		this.gameObjectIds = gameObjectIds;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public static ShipSize fromWorldEntityType(int worldEntityType)
	{
		switch (worldEntityType)
		{
			case 1:
				return RAFT;
			case 2:
				return SKIFF;
			case 3:
				return SLOOP;
			default:
				return null;
		}
	}

	public static ShipSize fromGameObjectId(int objectId)
	{
		for (ShipSize size : values())
		{
			for (int candidate : size.gameObjectIds)
			{
				if (candidate == objectId)
				{
					return size;
				}
			}
		}

		return null;
	}
}
