package com.salvagingafktimer;

public enum PlayerActivity
{
	SALVAGING("Salvaging"),
	SORTING_SALVAGE("Sorting salvage"),
	IDLE("Idle"),
	OTHER("Other");

	private final String displayName;

	PlayerActivity(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}
}
