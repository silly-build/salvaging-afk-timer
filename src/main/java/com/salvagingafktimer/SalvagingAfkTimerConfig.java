package com.salvagingafktimer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(SalvagingAfkTimerConfig.GROUP)
public interface SalvagingAfkTimerConfig extends Config
{
    String GROUP = "salvagingAfkTimer";

    enum TextAlignment
    {
        LEFT,
        CENTRE,
        RIGHT
    }

    @ConfigItem(
            keyName = "toggleHotkey",
            name = "Toggle Hotkey",
            description = "Toggles the dimmer and timer overlay on or off",
            position = 0
    )
    default Keybind toggleHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "toggleOnlyHidesDimmer",
            name = "Toggle only hides dimmer",
            description = "When enabled, the toggle hotkey hides only the dimmer and leaves the timer text enabled",
            position = 1
    )
    default boolean toggleOnlyHidesDimmer()
    {
        return true;
    }

    @ConfigSection(
            name = "Distraction Dimmer",
            description = "Controls automatic dimming while salvaging or sorting",
            position = 2
    )
    String dimmerSection = "dimmer";

    @ConfigItem(
            keyName = "dimmerColor",
            name = "Dimmer colour",
            description = "Colour used for the distraction dimmer",
            section = dimmerSection,
            position = 0
    )
    default Color dimmerColor()
    {
        return Color.BLACK;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "dimmerOpacity",
            name = "Dimmer opacity",
            description = "Opacity percentage for the dimmer",
            section = dimmerSection,
            position = 1
    )
    default int dimmerOpacity()
    {
        return 50;
    }

    @ConfigItem(
            keyName = "dimWhenSalvaging",
            name = "Dim when salvaging",
            description = "Dims the screen while the player is actively salvaging",
            section = dimmerSection,
            position = 2
    )
    default boolean dimWhenSalvaging()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dimWhenSorting",
            name = "Dim when sorting",
            description = "Dims the screen while the player is actively sorting salvage",
            section = dimmerSection,
            position = 3
    )
    default boolean dimWhenSorting()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dimWhenCrewSalvaging",
            name = "Dim when crew salvaging",
            description = "Dims the screen while idle if one or more crewmates are salvaging into the cargo hold",
            section = dimmerSection,
            position = 4
    )
    default boolean dimWhenCrewSalvaging()
    {
        return false;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "cargoHoldUndimPercent",
            name = "Cargo hold undim %",
            description = "Removes the crew salvaging dimmer once the cargo hold reaches this percentage full",
            section = dimmerSection,
            position = 5
    )
    default int cargoHoldUndimPercent()
    {
        return 100;
    }

    @ConfigItem(
            keyName = "dimWhenNoWrecks",
            name = "Dim when no wrecks",
            description = "Dims the screen while idle and no nearby wrecks are available, undimming when one respawns",
            section = dimmerSection,
            position = 6
    )
    default boolean dimWhenNoWrecks()
    {
        return false;
    }

    @ConfigItem(
            keyName = "undimSoonerWhenSorting",
            name = "Undim sooner when sorting",
            description = "Removes the dimmer when the sorting ETA reaches the configured threshold",
            section = dimmerSection,
            position = 7
    )
    default boolean undimSoonerWhenSorting()
    {
        return false;
    }

    @Range(min = 0, max = 60)
    @ConfigItem(
            keyName = "sortingUndimSeconds",
            name = "Sorting undim seconds",
            description = "Seconds remaining at which sorting should undim",
            section = dimmerSection,
            position = 8
    )
    default int sortingUndimSeconds()
    {
        return 3;
    }

    @ConfigItem(
            keyName = "unhideChat",
            name = "Unhide chat",
            description = "Leaves the chatbox visible while the rest of the screen is dimmed",
            section = dimmerSection,
            position = 9
    )
    default boolean unhideChat()
    {
        return false;
    }

    @ConfigItem(
            keyName = "unhideInterfaces",
            name = "Unhide interfaces",
            description = "Dims only the playable viewport and leaves all interfaces visible",
            section = dimmerSection,
            position = 10
    )
    default boolean unhideInterfaces()
    {
        return true;
    }

    @ConfigSection(
            name = "Timer Overlay",
            description = "Controls the movable Alt-drag timer and status block",
            position = 3
    )
    String timerOverlaySection = "timerOverlay";

    @ConfigItem(
            keyName = "showMovableTextOverlay",
            name = "Show timer overlay",
            description = "Displays the movable salvaging status block",
            section = timerOverlaySection,
            position = 0
    )
    default boolean showMovableTextOverlay()
    {
        return false;
    }

    @ConfigItem(
            keyName = "showMinimalistTextBox",
            name = "Minimalist text box",
            description = "Displays the same timer information in a separate compact RuneScape-style text box",
            section = timerOverlaySection,
            position = 1
    )
    default boolean showMinimalistTextBox()
    {
        return true;
    }

    @ConfigItem(
            keyName = "onlyShowTimerOverlayWhenDimmerActive",
            name = "Only show text when dimmed",
            description = "Shows enabled timer text overlays only while the screen is currently dimmed",
            section = timerOverlaySection,
            position = 2
    )
    default boolean onlyShowTimerOverlayWhenDimmerActive()
    {
        return false;
    }

    @ConfigItem(
            keyName = "showSalvagingEtaOverlay",
            name = "Show salvaging ETA",
            description = "Shows the salvaging interaction ETA while actively salvaging",
            section = timerOverlaySection,
            position = 3
    )
    default boolean showSalvagingEtaOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showSortingEtaOverlay",
            name = "Show sorting ETA",
            description = "Shows time until sorting completes while actively sorting",
            section = timerOverlaySection,
            position = 4
    )
    default boolean showSortingEtaOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showWreckTimerOverlay",
            name = "Show wreck timer",
            description = "Adds the wreck availability countdown after the active wreck count",
            section = timerOverlaySection,
            position = 5
    )
    default boolean showWreckTimerOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showCargoOverlay",
            name = "Show cargo",
            description = "Shows the estimated cargo quantity",
            section = timerOverlaySection,
            position = 6
    )
    default boolean showCargoOverlay()
    {
        return true;
    }

    @Range(min = 14, max = 96)
    @ConfigItem(
            keyName = "etaTextSize",
            name = "ETA text size",
            description = "Larger font size used for the active ETA line",
            section = timerOverlaySection,
            position = 7
    )
    default int etaTextSize()
    {
        return 25;
    }

    @Range(min = 12, max = 72)
    @ConfigItem(
            keyName = "statusTextSize",
            name = "Text size",
            description = "Font size for cargo, extractor and wreck information",
            section = timerOverlaySection,
            position = 8
    )
    default int statusTextSize()
    {
        return 18;
    }

    @ConfigItem(
            keyName = "showStatusBackground",
            name = "Show background",
            description = "Draws a coloured background behind the movable text block",
            section = timerOverlaySection,
            position = 9
    )
    default boolean showStatusBackground()
    {
        return false;
    }

    @ConfigItem(
            keyName = "statusBackgroundColor",
            name = "Background colour",
            description = "Colour used behind the movable text block",
            section = timerOverlaySection,
            position = 10
    )
    default Color statusBackgroundColor()
    {
        return Color.BLACK;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "statusBackgroundOpacity",
            name = "Background opacity",
            description = "Opacity percentage for the movable text background",
            section = timerOverlaySection,
            position = 11
    )
    default int statusBackgroundOpacity()
    {
        return 65;
    }

    @ConfigItem(
            keyName = "statusTextColor",
            name = "Text colour",
            description = "Colour used for the movable overlay text",
            section = timerOverlaySection,
            position = 12
    )
    default Color statusTextColor()
    {
        return Color.WHITE;
    }

    @ConfigItem(
            keyName = "statusTextAlignment",
            name = "Text alignment",
            description = "Aligns text in both the movable and minimalist overlays",
            section = timerOverlaySection,
            position = 13
    )
    default TextAlignment statusTextAlignment()
    {
        return TextAlignment.RIGHT;
    }

    @ConfigSection(
            name = "Crystal Extractor",
            description = "Controls Crystal Extractor status and dimmer behaviour",
            position = 4
    )
    String crystalExtractorSection = "crystalExtractor";

    @ConfigItem(
            keyName = "showExtractorOverlay",
            name = "Show crystal extractor timer",
            description = "Shows the Crystal Extractor state or countdown",
            section = crystalExtractorSection,
            position = 0
    )
    default boolean showExtractorOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "pulseExtractorReady",
            name = "Pulse when extractor ready",
            description = "Pulses the Crystal Extractor line in blue when it is ready",
            section = crystalExtractorSection,
            position = 1
    )
    default boolean pulseExtractorReady()
    {
        return true;
    }

    @Range(min = 250, max = 4000)
    @ConfigItem(
            keyName = "extractorPulseSpeed",
            name = "Extractor pulse speed",
            description = "Milliseconds for one full blue pulse",
            section = crystalExtractorSection,
            position = 2
    )
    default int extractorPulseSpeed()
    {
        return 1200;
    }

    @ConfigItem(
            keyName = "undimWhenExtractorReady",
            name = "Undim when extractor ready",
            description = "Turns off the dimmer whenever the Crystal Extractor is ready",
            section = crystalExtractorSection,
            position = 3
    )
    default boolean undimWhenExtractorReady()
    {
        return false;
    }

    @ConfigSection(
            name = "Tracking",
            description = "Advanced tracking options",
            position = 5
    )
    String trackingSection = "tracking";


    @Range(min = 60, max = 600)
    @ConfigItem(
            keyName = "activeRateWindowSeconds",
            name = "Active rate window",
            description = "Moving-average window using only active salvaging time, up to the most recent 10 minutes",
            section = trackingSection,
            position = 0
    )
    default int rateWindowSeconds()
    {
        return 600;
    }

    @ConfigItem(
            keyName = "shipSizeOverride",
            name = "Ship size override",
            description = "Leave on Auto unless object detection cannot identify the hull",
            section = trackingSection,
            position = 1
    )
    default ShipSize shipSizeOverride()
    {
        return ShipSize.AUTO;
    }

    @ConfigItem(
            keyName = "cargoHoldTierOverride",
            name = "Cargo hold override",
            description = "Leave on Auto unless object detection cannot identify the cargo hold tier",
            section = trackingSection,
            position = 2
    )
    default CargoHoldTier cargoHoldTierOverride()
    {
        return CargoHoldTier.AUTO;
    }

}
