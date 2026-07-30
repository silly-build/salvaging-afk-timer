package com.salvagingafktimer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Legacy diagnostic overlay retained for source compatibility.
 *
 * The live plugin uses {@link SalvagingStatusOverlay}. This class is not added
 * to the OverlayManager, but keeping it compilable avoids failures in projects
 * that still contain the older source file.
 */
public class ShipwreckSalvageOverlay extends Overlay
{
	private final SalvagingAfkTimerPlugin plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	public ShipwreckSalvageOverlay(SalvagingAfkTimerPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panelComponent.setPreferredSize(new Dimension(380, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Salvaging AFK Timer")
			.build());

		addLine("Player state", plugin.getPlayerActivity().getDisplayName());
		addLine("Crystal extractor", plugin.getCrystalExtractorStatus());
		addLine("Ship", plugin.getShipDescription());
		addLine("Cargo capacity", plugin.getCargoCapacityText());
		addLine("Cargo confirmed", plugin.getConfirmedCargoText());
		addLine("Cargo estimated", plugin.getEstimatedCargoText());
		addLine("Inventory", plugin.getInventoryText());
		addLine("Salvaging rate", plugin.getSalvageRateText());
		addLine("Inventory full ETA", plugin.getInventoryFullEtaText());
		addLine("Sorting ETA", plugin.getSortingEtaText());
		addLine("Potentially no wreck", plugin.getTimeUntilPotentiallyNoWreckText());
		addLine("Wreck available", plugin.getTimeUntilWreckAvailableText());

		List<SalvagingAfkTimerPlugin.WreckDisplay> wrecks = plugin.getNearestWrecks(2);
		for (int index = 0; index < 2; index++)
		{
			if (index < wrecks.size())
			{
				SalvagingAfkTimerPlugin.WreckDisplay wreck = wrecks.get(index);
				addLine("Wreck " + (index + 1), wreck.getTitle());
				addLine("  status", wreck.getStatus());
			}
			else
			{
				addLine("Wreck " + (index + 1), "Not detected");
			}
		}

		return panelComponent.render(graphics);
	}

	private void addLine(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right == null ? "" : right)
			.build());
	}
}
