package com.pluginimporter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

public class ProfileDisplayOverlay extends OverlayPanel {

    private final ProfilePlugin plugin;
    private final ProfileConfig config;

    // The duration in milliseconds for the HUD to be visible.
    private static final int DISPLAY_DURATION_MS = 10000; // 10 seconds

    @Inject
    private ProfileDisplayOverlay(ProfilePlugin plugin, ProfileConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setResizable(false);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Only render if the config option is enabled
        if (!config.showLoadedProfileHud()) {
            return null;
        }

        // Check if the display duration has passed
        if (System.currentTimeMillis() - plugin.getDisplayTimestamp() > DISPLAY_DURATION_MS) {
            return null;
        }

        List<String> displayLines = plugin.getDisplayLines();
        if (displayLines == null || displayLines.isEmpty()) {
            return null;
        }

        // The first line is always the profile name, which we'll use as the title
        String profileName = displayLines.get(0);
        panelComponent.getChildren().add(TitleComponent.builder()
                .text(profileName)
                .build());

        // Add each subsequent line (the plugin groups that were loaded)
        for (int i = 1; i < displayLines.size(); i++) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(displayLines.get(i))
                    .build());
        }

        return super.render(graphics);
    }
}
