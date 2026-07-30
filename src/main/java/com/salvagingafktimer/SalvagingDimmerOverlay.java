package com.salvagingafktimer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class SalvagingDimmerOverlay extends Overlay
{
	private final Client client;
	private final SalvagingAfkTimerPlugin plugin;
	private final SalvagingAfkTimerConfig config;

	@Inject
	public SalvagingDimmerOverlay(
			Client client,
			SalvagingAfkTimerPlugin plugin,
			SalvagingAfkTimerConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
		setMovable(false);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.shouldShowDimmer())
		{
			return null;
		}

		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		if (canvasWidth <= 0 || canvasHeight <= 0)
		{
			return null;
		}

		Rectangle canvasBounds = new Rectangle(0, 0, canvasWidth, canvasHeight);
		Area dimmedArea = buildDimmedArea(canvasBounds);
		if (dimmedArea == null || dimmedArea.isEmpty())
		{
			return null;
		}

		Color configured = config.dimmerColor();
		if (configured == null)
		{
			configured = Color.BLACK;
		}

		int opacity = Math.max(0, Math.min(100, config.dimmerOpacity()));
		int alpha = Math.round(opacity * 255.0f / 100.0f);
		Color dimmerColor = new Color(
				configured.getRed(),
				configured.getGreen(),
				configured.getBlue(),
				alpha
		);

		Graphics2D dimmerGraphics = (Graphics2D) graphics.create();
		try
		{
			dimmerGraphics.setColor(dimmerColor);
			dimmerGraphics.fill(dimmedArea);
		}
		finally
		{
			dimmerGraphics.dispose();
		}

		return new Dimension(canvasWidth, canvasHeight);
	}

	private Area buildDimmedArea(Rectangle canvasBounds)
	{
		if (config.unhideInterfaces())
		{
			Rectangle viewportBounds = getVisibleViewportBounds(canvasBounds);
			return viewportBounds == null ? null : new Area(viewportBounds);
		}

		Area dimmedArea = new Area(canvasBounds);
		if (config.unhideChat())
		{
			Rectangle chatBounds = findVisibleChatBounds(canvasBounds);
			if (chatBounds != null)
			{
				dimmedArea.subtract(new Area(chatBounds));
			}
		}

		return dimmedArea;
	}

	private Rectangle getVisibleViewportBounds(Rectangle canvasBounds)
	{
		Rectangle viewport = new Rectangle(
				client.getViewportXOffset(),
				client.getViewportYOffset(),
				client.getViewportWidth(),
				client.getViewportHeight()
		);

		return intersectVisibleBounds(viewport, canvasBounds);
	}

	@SuppressWarnings("deprecation")
	private Rectangle findVisibleChatBounds(Rectangle canvasBounds)
	{
		Widget[] candidates = new Widget[]
		{
			client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_CHATBOX_PARENT),
			client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT),
			client.getWidget(WidgetInfo.CHATBOX_PARENT)
		};

		for (Widget widget : candidates)
		{
			if (widget == null || widget.isHidden())
			{
				continue;
			}

			Rectangle visibleBounds = intersectVisibleBounds(widget.getBounds(), canvasBounds);
			if (visibleBounds != null)
			{
				return visibleBounds;
			}
		}

		return null;
	}

	private static Rectangle intersectVisibleBounds(Rectangle bounds, Rectangle canvasBounds)
	{
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}

		Rectangle intersection = bounds.intersection(canvasBounds);
		return intersection.width > 0 && intersection.height > 0 ? intersection : null;
	}
}
