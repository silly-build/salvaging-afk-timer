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
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class SalvagingStatusOverlay extends Overlay
{
    private static final int HORIZONTAL_PADDING = 12;
    private static final int VERTICAL_PADDING = 10;
    private static final int LINE_GAP = 5;
    private static final int MINIMUM_BOX_WIDTH = 280;
    private static final Color EXTRACTOR_READY_BLUE = new Color(80, 190, 255);

    private static final String[] ETA_WIDTH_TEMPLATES =
            {
                    "Salvaging ETA: Calibrating (8)",
                    "Salvaging ETA: Wreck soonish",
                    "Salvaging ETA: 99:59",
                    "Sorting ETA: 99:59",
                    "IDLE"
            };

    private static final String[] NORMAL_WIDTH_TEMPLATES =
            {
                    "Crystal Extractor: Active, timer start unknown",
                    "Crystal Extractor: 99:59",
                    "Crystal Extractor: ...",
                    "Cargo: Please open cargo to sync",
                    "Cargo: 9999/9999",
                    "Wrecks: 2 ⏐ Respawn in 99:59 max",
                    "Wrecks: 2 ⏐ calibrating"
            };

    private final SalvagingAfkTimerPlugin plugin;
    private final SalvagingAfkTimerConfig config;

    @Inject
    public SalvagingStatusOverlay(
            SalvagingAfkTimerPlugin plugin,
            SalvagingAfkTimerConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setPreferredPosition(OverlayPosition.TOP_CENTER);
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
        if (!plugin.shouldShowTimerOverlay())
        {
            return null;
        }

        List<OverlayLine> lines = buildLines();
        if (lines.isEmpty())
        {
            return null;
        }

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        Font normalFont = createNormalFont();
        Font etaFont = createEtaFont();
        int stableTextWidth = calculateStableTextWidth(graphics, lines, normalFont, etaFont);

        int textHeight = 0;
        for (OverlayLine line : lines)
        {
            graphics.setFont(line.font);
            textHeight += graphics.getFontMetrics().getHeight();
        }

        int totalGapHeight = LINE_GAP * Math.max(0, lines.size() - 1);
        int boxWidth = stableTextWidth + HORIZONTAL_PADDING * 2;
        int boxHeight = textHeight + totalGapHeight + VERTICAL_PADDING * 2;

        if (config.showStatusBackground())
        {
            Color configuredBackground = config.statusBackgroundColor();
            int opacityPercent = Math.max(0, Math.min(100, config.statusBackgroundOpacity()));
            int alpha = Math.round(opacityPercent * 255.0f / 100.0f);
            Color background = new Color(
                    configuredBackground.getRed(),
                    configuredBackground.getGreen(),
                    configuredBackground.getBlue(),
                    alpha
            );
            graphics.setColor(background);
            graphics.fillRoundRect(0, 0, boxWidth, boxHeight, 8, 8);
        }

        int y = VERTICAL_PADDING;
        for (OverlayLine line : lines)
        {
            graphics.setFont(line.font);
            FontMetrics metrics = graphics.getFontMetrics();
            y += metrics.getAscent();

            int textWidth = metrics.stringWidth(line.text);
            int x = calculateTextX(boxWidth, textWidth);

            graphics.setColor(Color.BLACK);
            graphics.drawString(line.text, x + 1, y + 1);
            graphics.setColor(line.color);
            graphics.drawString(line.text, x, y);

            y += metrics.getDescent() + metrics.getLeading() + LINE_GAP;
        }

        return new Dimension(boxWidth, boxHeight);
    }

    private List<OverlayLine> buildLines()
    {
        List<OverlayLine> lines = new ArrayList<>();
        Font normalFont = createNormalFont();
        Font etaFont = createEtaFont();
        Color normalColor = config.statusTextColor();

        PlayerActivity activity = plugin.getPlayerActivity();
        if (activity == PlayerActivity.SALVAGING && config.showSalvagingEtaOverlay())
        {
            lines.add(new OverlayLine(
                    "Salvaging ETA: " + plugin.getSalvagingOverlayEtaText(),
                    etaFont,
                    normalColor
            ));
        }
        else if (activity == PlayerActivity.SORTING_SALVAGE && config.showSortingEtaOverlay())
        {
            lines.add(new OverlayLine(
                    "Sorting ETA: " + plugin.getSortingEtaText(),
                    etaFont,
                    normalColor
            ));
        }
        else if (config.showSalvagingEtaOverlay() || config.showSortingEtaOverlay())
        {
            lines.add(new OverlayLine("IDLE", etaFont, normalColor));
        }

        if (config.showExtractorOverlay())
        {
            Color extractorColor = normalColor;
            if (config.pulseExtractorReady() && plugin.isCrystalExtractorReadyForOverlay())
            {
                extractorColor = calculatePulseColor(normalColor);
            }

            lines.add(new OverlayLine(
                    "Crystal Extractor: " + plugin.getCrystalExtractorStatus(),
                    normalFont,
                    extractorColor
            ));
        }

        if (config.showCargoOverlay())
        {
            lines.add(new OverlayLine(
                    "Cargo: " + plugin.getEstimatedCargoOverlayText(),
                    normalFont,
                    normalColor
            ));
        }

        String wreckText = "Wrecks: " + plugin.getNearbyAvailableWreckCount();
        if (config.showWreckTimerOverlay())
        {
            wreckText += " ⏐ " + plugin.getPotentiallyNoWreckCompactText();
        }
        lines.add(new OverlayLine(wreckText, normalFont, normalColor));

        return lines;
    }

    private Font createNormalFont()
    {
        return new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                Math.max(12, config.statusTextSize())
        );
    }

    private Font createEtaFont()
    {
        return new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                Math.max(14, config.etaTextSize())
        );
    }

    private int calculateStableTextWidth(
            Graphics2D graphics,
            List<OverlayLine> lines,
            Font normalFont,
            Font etaFont)
    {
        int width = MINIMUM_BOX_WIDTH - HORIZONTAL_PADDING * 2;

        graphics.setFont(etaFont);
        FontMetrics etaMetrics = graphics.getFontMetrics();
        for (String template : ETA_WIDTH_TEMPLATES)
        {
            width = Math.max(width, etaMetrics.stringWidth(template));
        }

        graphics.setFont(normalFont);
        FontMetrics normalMetrics = graphics.getFontMetrics();
        for (String template : NORMAL_WIDTH_TEMPLATES)
        {
            width = Math.max(width, normalMetrics.stringWidth(template));
        }

        for (OverlayLine line : lines)
        {
            graphics.setFont(line.font);
            width = Math.max(width, graphics.getFontMetrics().stringWidth(line.text));
        }

        return width;
    }

    private int calculateTextX(int boxWidth, int textWidth)
    {
        switch (config.statusTextAlignment())
        {
            case LEFT:
                return HORIZONTAL_PADDING;
            case RIGHT:
                return boxWidth - HORIZONTAL_PADDING - textWidth;
            case CENTRE:
            default:
                return (boxWidth - textWidth) / 2;
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

    private static final class OverlayLine
    {
        private final String text;
        private final Font font;
        private final Color color;

        private OverlayLine(String text, Font font, Color color)
        {
            this.text = text;
            this.font = font;
            this.color = color;
        }
    }
}