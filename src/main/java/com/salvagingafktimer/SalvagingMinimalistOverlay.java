package com.salvagingafktimer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class SalvagingMinimalistOverlay extends Overlay
{
    private static final int HORIZONTAL_PADDING = 6;
    private static final int VERTICAL_PADDING = 4;
    private static final int LINE_GAP = 1;
    private static final float MINIMALIST_FONT_SIZE = 16.0f;

    private static final Color BOX_BACKGROUND = new Color(69, 65, 61, 238);
    private static final Color OUTER_BORDER = new Color(34, 31, 28, 255);
    private static final Color TOP_LEFT_BORDER = new Color(112, 107, 100, 255);
    private static final Color BOTTOM_RIGHT_BORDER = new Color(46, 42, 38, 255);
    private static final Color EXTRACTOR_READY_BLUE = new Color(80, 190, 255);

    private static final String[] WIDTH_REFERENCE_LINES =
            {
                    "Salvaging ETA: Calibrating (8)",
                    "Salvaging ETA: 99:59:59",
                    "Sorting ETA: 0:59",
                    "Extractor: Timer unknown",
                    "Extractor: 0:59",
                    "Cargo: Open cargo to sync",
                    "Cargo: 999/999",
                    "Wrecks: 0 | Respawn 99:59 max",
                    "Wrecks: 0 | unknown ETA",
                    "Wrecks: 0 | Calibrating",
                    "Wrecks: 2 | Timer stale"
            };

    private final SalvagingAfkTimerPlugin plugin;
    private final SalvagingAfkTimerConfig config;

    @Inject
    public SalvagingMinimalistOverlay(
            SalvagingAfkTimerPlugin plugin,
            SalvagingAfkTimerConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.BOTTOM_RIGHT);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGHEST);
        setMovable(true);
        setSnappable(true);
        setResettable(true);
        setResizable(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.shouldShowMinimalistTimerOverlay())
        {
            return null;
        }

        List<MinimalistLine> lines = buildLines();
        if (lines.isEmpty())
        {
            return null;
        }

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
        );

        Font font = FontManager.getRunescapeSmallFont()
                .deriveFont(MINIMALIST_FONT_SIZE);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();

        int textWidth = calculateFixedTextWidth(metrics);
        int lineHeight = metrics.getHeight();
        int textHeight = lineHeight * lines.size()
                + LINE_GAP * Math.max(0, lines.size() - 1);
        int boxWidth = textWidth + HORIZONTAL_PADDING * 2;
        int boxHeight = textHeight + VERTICAL_PADDING * 2;

        drawBox(graphics, boxWidth, boxHeight);

        int y = VERTICAL_PADDING + metrics.getAscent();
        for (MinimalistLine line : lines)
        {
            int x = calculateTextX(metrics, line.text, textWidth);

            graphics.setColor(Color.BLACK);
            graphics.drawString(line.text, x + 1, y + 1);
            graphics.setColor(line.color);
            graphics.drawString(line.text, x, y);

            y += lineHeight + LINE_GAP;
        }

        return new Dimension(boxWidth, boxHeight);
    }

    private List<MinimalistLine> buildLines()
    {
        List<MinimalistLine> lines = new ArrayList<>();
        Color normalColor = config.statusTextColor();

        PlayerActivity activity = plugin.getPlayerActivity();
        if (activity == PlayerActivity.SALVAGING && config.showSalvagingEtaOverlay())
        {
            lines.add(new MinimalistLine(
                    "Salvaging ETA: " + plugin.getSalvagingOverlayEtaText(),
                    normalColor
            ));
        }
        else if (activity == PlayerActivity.SORTING_SALVAGE && config.showSortingEtaOverlay())
        {
            lines.add(new MinimalistLine(
                    "Sorting ETA: " + plugin.getSortingEtaText(),
                    normalColor
            ));
        }
        else if (config.showSalvagingEtaOverlay() || config.showSortingEtaOverlay())
        {
            lines.add(new MinimalistLine("IDLE", normalColor));
        }

        if (config.showExtractorOverlay() && plugin.isCrystalExtractorDetectedForOverlay())
        {
            Color extractorColor = normalColor;
            if (config.pulseExtractorReady() && plugin.isCrystalExtractorReadyForOverlay())
            {
                extractorColor = calculatePulseColor(normalColor);
            }

            lines.add(new MinimalistLine(
                    "Extractor: " + compactExtractorStatus(plugin.getCrystalExtractorStatus()),
                    extractorColor
            ));
        }

        if (config.showCargoOverlay())
        {
            lines.add(new MinimalistLine(
                    "Cargo: " + compactCargoStatus(plugin.getEstimatedCargoOverlayText()),
                    normalColor
            ));
        }

        String wreckText = "Wrecks: " + plugin.getNearbyAvailableWreckCount();
        if (config.showWreckTimerOverlay())
        {
            wreckText += " | " + compactWreckStatus(plugin.getPotentiallyNoWreckCompactText());
        }
        lines.add(new MinimalistLine(wreckText, normalColor));

        return lines;
    }

    private int calculateFixedTextWidth(FontMetrics metrics)
    {
        int width = 0;
        for (String referenceLine : WIDTH_REFERENCE_LINES)
        {
            width = Math.max(width, metrics.stringWidth(referenceLine));
        }
        return width;
    }

    private int calculateTextX(FontMetrics metrics, String text, int textAreaWidth)
    {
        int lineWidth = metrics.stringWidth(text);

        switch (config.statusTextAlignment())
        {
            case CENTRE:
                return HORIZONTAL_PADDING + Math.max(0, (textAreaWidth - lineWidth) / 2);
            case RIGHT:
                return HORIZONTAL_PADDING + Math.max(0, textAreaWidth - lineWidth);
            case LEFT:
            default:
                return HORIZONTAL_PADDING;
        }
    }

    private String compactExtractorStatus(String status)
    {
        if ("Active, timer start unknown".equals(status))
        {
            return "Timer unknown";
        }

        return status;
    }

    private String compactCargoStatus(String status)
    {
        if ("Please open cargo to sync".equals(status))
        {
            return "Open cargo to sync";
        }

        return status;
    }

    private String compactWreckStatus(String status)
    {
        if ("Respawn calibrating".equals(status) || "calibrating".equals(status))
        {
            return "Calibrating";
        }

        if ("unlucky spawns, unknown ETA".equals(status))
        {
            return "unknown ETA";
        }

        if ("Wreck soonish".equals(status))
        {
            return "Soonish";
        }

        if ("Wreck soon".equals(status))
        {
            return "Soon";
        }

        if ("Wreck timing stale".equals(status))
        {
            return "Timer stale";
        }

        if (status.startsWith("Respawn in "))
        {
            return "Respawn " + status.substring("Respawn in ".length());
        }

        return status;
    }

    private void drawBox(Graphics2D graphics, int width, int height)
    {
        graphics.setColor(BOX_BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        graphics.setColor(OUTER_BORDER);
        graphics.drawRect(0, 0, width - 1, height - 1);

        if (width > 3 && height > 3)
        {
            graphics.setColor(TOP_LEFT_BORDER);
            graphics.drawLine(1, 1, width - 2, 1);
            graphics.drawLine(1, 1, 1, height - 2);

            graphics.setColor(BOTTOM_RIGHT_BORDER);
            graphics.drawLine(1, height - 2, width - 2, height - 2);
            graphics.drawLine(width - 2, 1, width - 2, height - 2);
        }
    }

    private Color calculatePulseColor(Color base)
    {
        int periodMillis = Math.max(250, config.extractorPulseSpeed());
        double phase = (System.currentTimeMillis() % periodMillis) / (double) periodMillis;
        double blend = 0.25 + 0.75 * ((Math.sin(phase * Math.PI * 2.0) + 1.0) / 2.0);

        return new Color(
                mix(base.getRed(), EXTRACTOR_READY_BLUE.getRed(), blend),
                mix(base.getGreen(), EXTRACTOR_READY_BLUE.getGreen(), blend),
                mix(base.getBlue(), EXTRACTOR_READY_BLUE.getBlue(), blend)
        );
    }

    private static int mix(int start, int end, double amount)
    {
        return (int) Math.round(start + (end - start) * amount);
    }

    private static final class MinimalistLine
    {
        private final String text;
        private final Color color;

        private MinimalistLine(String text, Color color)
        {
            this.text = text;
            this.color = color;
        }
    }
}
