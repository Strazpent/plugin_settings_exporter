package com.pluginimporter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("profile")
public interface ProfileConfig extends Config {

    @ConfigItem(
            keyName = "showLoadedProfileHud",
            name = "Show Loaded Profile HUD",
            description = "Displays a heads-up message showing which profile and plugins were loaded.",
            position = 1
    )
    default boolean showLoadedProfileHud() {
        return true;
    }

    @ConfigItem(
            keyName = "profiles",
            name = "Profiles",
            description = "Saved profiles for plugin configurations.",
            hidden = true,
            position = 99
    )
    default String profiles() {
        return "";
    }
}
