package com.salvagingafktimer;

import net.runelite.api.gameval.ObjectID;

public enum ShipwreckType
{
	SMALL("Small", 15, 60,
		ObjectID.SAILING_SMALL_SHIPWRECK,
		ObjectID.SAILING_SMALL_SHIPWRECK_STUMP),
	FISHERMAN("Fisherman's", 26, 180,
		ObjectID.SAILING_FISHERMAN_SHIPWRECK,
		ObjectID.SAILING_FISHERMAN_SHIPWRECK_STUMP),
	BARRACUDA("Barracuda", 35, 180,
		ObjectID.SAILING_BARRACUDA_SHIPWRECK,
		ObjectID.SAILING_BARRACUDA_SHIPWRECK_STUMP),
	LARGE("Large", 53, 180,
		ObjectID.SAILING_LARGE_SHIPWRECK,
		ObjectID.SAILING_LARGE_SHIPWRECK_STUMP),
	PIRATE("Pirate", 64, 180,
		ObjectID.SAILING_PIRATE_SHIPWRECK,
		ObjectID.SAILING_PIRATE_SHIPWRECK_STUMP),
	MERCENARY("Mercenary", 73, 195,
		ObjectID.SAILING_MERCENARY_SHIPWRECK,
		ObjectID.SAILING_MERCENARY_SHIPWRECK_STUMP),
	FREMENNIK("Fremennik", 80, 225,
		ObjectID.SAILING_FREMENNIK_SHIPWRECK,
		ObjectID.SAILING_FREMENNIK_SHIPWRECK_STUMP),
	MERCHANT("Merchant", 87, 240,
		ObjectID.SAILING_MERCHANT_SHIPWRECK,
		ObjectID.SAILING_MERCHANT_SHIPWRECK_STUMP);

	private final String displayName;
	private final int requiredSailingLevel;
	private final int averageDurationSeconds;
	private final int activeObjectId;
	private final int stumpObjectId;

	ShipwreckType(
		String displayName,
		int requiredSailingLevel,
		int averageDurationSeconds,
		int activeObjectId,
		int stumpObjectId)
	{
		this.displayName = displayName;
		this.requiredSailingLevel = requiredSailingLevel;
		this.averageDurationSeconds = averageDurationSeconds;
		this.activeObjectId = activeObjectId;
		this.stumpObjectId = stumpObjectId;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int getRequiredSailingLevel()
	{
		return requiredSailingLevel;
	}

	public int getAverageDurationSeconds()
	{
		return averageDurationSeconds;
	}

	public int getActiveObjectId()
	{
		return activeObjectId;
	}

	public int getStumpObjectId()
	{
		return stumpObjectId;
	}

	public static ShipwreckType fromActiveObjectId(int objectId)
	{
		for (ShipwreckType type : values())
		{
			if (type.activeObjectId == objectId)
			{
				return type;
			}
		}

		return null;
	}

	public static ShipwreckType fromStumpObjectId(int objectId)
	{
		for (ShipwreckType type : values())
		{
			if (type.stumpObjectId == objectId)
			{
				return type;
			}
		}

		return null;
	}

	public static ShipwreckType fromAnyObjectId(int objectId)
	{
		ShipwreckType active = fromActiveObjectId(objectId);
		return active != null ? active : fromStumpObjectId(objectId);
	}

	public static boolean isActiveObjectId(int objectId)
	{
		return fromActiveObjectId(objectId) != null;
	}

	public static boolean isStumpObjectId(int objectId)
	{
		return fromStumpObjectId(objectId) != null;
	}
}
