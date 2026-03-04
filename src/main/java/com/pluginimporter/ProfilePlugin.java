package com.pluginimporter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Profile Manager",
        description = "Save and load profiles for plugin configurations.",
        tags = {"profile", "manager", "config", "settings"}
)
public class ProfilePlugin extends Plugin {

    public static final String DEFAULT_PROFILE_NAME = "Default";
    private static final String CONFIG_GROUP = "profile";
    private static final String PROFILES_KEY = "profiles";
    private static final String ICON = "icon.png";
    private static final String RUNELITE_GROUP = "runelite";
    private static final String ENABLED_SUFFIX = "_enabled";

    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;
    @Inject private PluginManager pluginManager;
    @Inject private OverlayManager overlayManager;
    @Inject private ProfileDisplayOverlay profileDisplayOverlay;
    @Inject private Gson gson;
    @Inject private KeyManager keyManager;

    private ProfilePanel panel;
    private NavigationButton navButton;
    private Map<String, Profile> profiles = new HashMap<>();
    private String lastArea;
    @Getter private String activeProfileName;
    @Getter private final List<String> displayLines = new ArrayList<>();
    @Getter private long displayTimestamp = 0;

    private final Map<String, Plugin> groupToPluginMap = new HashMap<>();
    private final Map<String, String> classNameToConfigGroupMap = new HashMap<>();
    private final Map<String, HotkeyListener> hotkeyListeners = new HashMap<>();

    @Override
    protected void startUp() throws Exception {
        buildPluginMaps();
        panel = new ProfilePanel(this);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), ICON);
        navButton = NavigationButton.builder()
                .tooltip("Profile Manager")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
        overlayManager.add(profileDisplayOverlay);
        loadProfiles();
        activeProfileName = DEFAULT_PROFILE_NAME;
        panel.updateProfiles();
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(profileDisplayOverlay);
        clearHotkeyListeners();
    }

    private void buildPluginMaps() {
        groupToPluginMap.clear();
        classNameToConfigGroupMap.clear();
        for (Plugin plugin : pluginManager.getPlugins()) {
            String className = plugin.getClass().getSimpleName().toLowerCase();
            String pluginName = className.replace("plugin", "");
            classNameToConfigGroupMap.put(className, pluginName);
            groupToPluginMap.put(pluginName, plugin);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            checkArea();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() == GameState.LOGGED_IN) {
            checkArea();
        }
    }

    private void checkArea() {
        if (client.getLocalPlayer() == null) return;
        String currentArea = client.getLocalPlayer().getWorldLocation().toString();
        if (!currentArea.equals(lastArea)) {
            lastArea = currentArea;
            for (Profile profile : profiles.values()) {
                if (profile.getArea() != null && profile.getArea().equals(currentArea)) {
                    loadProfile(profile.getName());
                    break;
                }
            }
        }
    }

    public Map<String, String> captureCurrentConfig() {
        Map<String, String> config = new HashMap<>();
        buildPluginMaps();
        saveGroupSettings(config, RUNELITE_GROUP);
        for (String group : classNameToConfigGroupMap.values()) {
            saveGroupSettings(config, group);
        }
        return config;
    }

    private void saveGroupSettings(Map<String, String> config, String group) {
        for (String key : configManager.getConfigurationKeys(group)) {
            key = key.replace(group + ".", "");
            String value = configManager.getConfiguration(group, key);
            if (value != null) {
                config.put(group + "." + key, value);
            }
        }
    }

    void saveProfile(String name) {
        if (name == null || name.trim().isEmpty()) return;
        if (profiles.containsKey(name) && !name.equals(DEFAULT_PROFILE_NAME)) {
            int result = JOptionPane.showConfirmDialog(panel, "A profile with this name already exists. Overwrite it?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.NO_OPTION) return;
        }

        Map<String, String> configToSave;
        Map<String, String> currentConfig = captureCurrentConfig();
        if (DEFAULT_PROFILE_NAME.equals(name)) {
            configToSave = currentConfig;
        } else {
            configToSave = new HashMap<>();
            Profile defaultProfile = profiles.get(DEFAULT_PROFILE_NAME);
            Map<String, String> defaultConfig = (defaultProfile != null) ? defaultProfile.getConfig() : new HashMap<>();
            for (Map.Entry<String, String> entry : currentConfig.entrySet()) {
                String key = entry.getKey();
                String currentValue = entry.getValue();
                String defaultValue = defaultConfig.get(key);
                if (defaultValue == null || !currentValue.equals(defaultValue)) {
                    configToSave.put(key, currentValue);
                }
            }
        }
        Profile profile = profiles.get(name);
        if (profile == null) {
            profile = new Profile(name, configToSave);
        } else {
            profile.setConfig(configToSave);
        }
        profiles.put(name, profile);
        activeProfileName = name;
        saveProfiles();
        panel.updateProfiles();
    }

    void loadProfile(String name) {
        displayLines.clear();
        displayLines.add("Loaded Profile: " + name);
        if (!DEFAULT_PROFILE_NAME.equals(name)) {
            Profile defaultProfile = profiles.get(DEFAULT_PROFILE_NAME);
            if (defaultProfile != null) {
                applyConfig(defaultProfile.getConfig());
            }
        }
        Profile profileToLoad = profiles.get(name);
        if (profileToLoad == null) return;
        applyConfig(profileToLoad.getConfig());
        displayTimestamp = System.currentTimeMillis();
        activeProfileName = name;
        panel.updateProfiles();
    }

    private void applyConfig(Map<String, String> configMap) {
        Set<String> loadedGroups = new HashSet<>();
        configMap.forEach((compositeKey, value) -> {
            String[] parts = compositeKey.split("\\.", 2);
            if (parts.length == 2) {
                String group = parts[0];
                String key = parts[1];
                if (group.equals(RUNELITE_GROUP)) {
                    String pluginGroup = classNameToConfigGroupMap.get(key);
                    Plugin targetPlugin = groupToPluginMap.get(pluginGroup);
                    if (targetPlugin != null) {
                        boolean shouldBeEnabled = Boolean.parseBoolean(value);
                        if (pluginManager.isPluginEnabled(targetPlugin) != shouldBeEnabled) {
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    pluginManager.setPluginEnabled(targetPlugin, shouldBeEnabled);
                                    if (shouldBeEnabled) pluginManager.startPlugin(targetPlugin);
                                    else pluginManager.stopPlugin(targetPlugin);
                                } catch (Exception e) {
                                    log.error("Failed to toggle plugin state for {}", targetPlugin.getName(), e);
                                }
                            });
                        }
                    }
                }
                configManager.setConfiguration(group, key, value);
                loadedGroups.add(group);
            }
        });
    }

    void deleteProfile(String name) {
        if (DEFAULT_PROFILE_NAME.equals(name)) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "The Default profile cannot be deleted.", "Action Not Allowed", JOptionPane.WARNING_MESSAGE));
            return;
        }
        profiles.remove(name);
        if (name.equals(activeProfileName)) {
            loadProfile(DEFAULT_PROFILE_NAME);
        }
        saveProfiles();
        panel.updateProfiles();
    }

    void renameProfile(String oldName, String newName) {
        if (newName == null || newName.trim().isEmpty() || profiles.containsKey(newName)) {
            JOptionPane.showMessageDialog(panel, "Invalid or duplicate profile name.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Profile profile = profiles.remove(oldName);
        if (profile != null) {
            profile.setName(newName);
            profiles.put(newName, profile);
            if (oldName.equals(activeProfileName)) {
                activeProfileName = newName;
            }
            saveProfiles();
            panel.updateProfiles();
        }
    }

    void duplicateProfile(String sourceName, String newName) {
        if (newName == null || newName.trim().isEmpty() || profiles.containsKey(newName)) {
            JOptionPane.showMessageDialog(panel, "Invalid or duplicate profile name.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Profile sourceProfile = profiles.get(sourceName);
        if (sourceProfile != null) {
            Profile newProfile = new Profile(newName, new HashMap<>(sourceProfile.getConfig()));
            newProfile.setHotkey(Keybind.NOT_SET); // Don't copy hotkeys to avoid conflicts
            profiles.put(newName, newProfile);
            saveProfiles();
            panel.updateProfiles();
        }
    }

    public void updateProfileConfig(String profileName, Map<String, String> newConfig) {
        Profile profile = profiles.get(profileName);
        if (profile != null) {
            Map<String, String> configToSave;
            if (DEFAULT_PROFILE_NAME.equals(profileName)) {
                configToSave = newConfig;
            } else {
                configToSave = new HashMap<>();
                Profile defaultProfile = profiles.get(DEFAULT_PROFILE_NAME);
                Map<String, String> defaultConfig = (defaultProfile != null) ? defaultProfile.getConfig() : new HashMap<>();
                for (Map.Entry<String, String> entry : newConfig.entrySet()) {
                    String key = entry.getKey();
                    String currentValue = entry.getValue();
                    String defaultValue = defaultConfig.get(key);
                    if (defaultValue == null || !currentValue.equals(defaultValue)) {
                        configToSave.put(key, currentValue);
                    }
                }
            }
            profile.setConfig(configToSave);
            saveProfiles();
            if (profileName.equals(activeProfileName)) {
                loadProfile(profileName);
            }
        }
    }

    void setProfileArea(String name, String area) {
        Profile profile = profiles.get(name);
        if (profile != null) {
            profile.setArea(area);
            saveProfiles();
        }
    }

    void openHotkeySetter(String profileName) {
        Profile profile = profiles.get(profileName);
        if (profile == null) return;
        Keybind currentKeybind = profile.getHotkey() != null ? profile.getHotkey() : Keybind.NOT_SET;
        final JTextField keybindField = new JTextField(currentKeybind.toString());
        keybindField.setEditable(false);
        final Keybind[] newKeybind = {currentKeybind};
        keybindField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                newKeybind[0] = new Keybind(e);
                keybindField.setText(newKeybind[0].toString());
                e.consume();
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.add(new JLabel("Press a key to set the hotkey for '" + profileName + "'"), BorderLayout.NORTH);
        panel.add(keybindField, BorderLayout.CENTER);
        int result = JOptionPane.showConfirmDialog(this.panel, panel, "Set Hotkey", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            setProfileHotkey(profileName, newKeybind[0]);
        }
    }

    private void setProfileHotkey(String profileName, Keybind newHotkey) {
        Profile profile = profiles.get(profileName);
        if (profile != null) {
            profile.setHotkey(newHotkey);
            saveProfiles();
            rebuildHotkeyListeners();
            panel.updateProfiles();
        }
    }

    private void clearHotkeyListeners() {
        hotkeyListeners.values().forEach(listener -> keyManager.unregisterKeyListener(listener));
        hotkeyListeners.clear();
    }

    private void rebuildHotkeyListeners() {
        clearHotkeyListeners();
        for (Profile profile : profiles.values()) {
            if (profile.getHotkey() != null && !profile.getHotkey().equals(Keybind.NOT_SET)) {
                final String profileName = profile.getName();
                HotkeyListener listener = new HotkeyListener(() -> profile.getHotkey()) {
                    @Override
                    public void hotkeyPressed() {
                        if (profileName.equals(activeProfileName)) {
                            loadProfile(DEFAULT_PROFILE_NAME);
                        } else {
                            loadProfile(profileName);
                        }
                    }
                };
                hotkeyListeners.put(profileName, listener);
                keyManager.registerKeyListener(listener);
            }
        }
    }

    void exportProfilesForDebug() {
        File debugFile = new File(RuneLite.RUNELITE_DIR, "profile-manager-debug.json");
        try (FileWriter writer = new FileWriter(debugFile)) {
            gson.toJson(profiles, writer);
            log.info("Successfully exported profiles to {}", debugFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to export profiles for debugging", e);
        }
    }

    private void saveProfiles() {
        String json = gson.toJson(profiles);
        configManager.setConfiguration(CONFIG_GROUP, PROFILES_KEY, json);
    }

    private void loadProfiles() {
        String json = configManager.getConfiguration(CONFIG_GROUP, PROFILES_KEY);
        if (json != null) {
            profiles = gson.fromJson(json, new TypeToken<Map<String, Profile>>() {}.getType());
        }
        if (profiles == null) {
            profiles = new HashMap<>();
        }
        boolean needsSave = false;
        for (Profile profile : profiles.values()) {
            if (profile.getHotkey() == null) {
                profile.setHotkey(Keybind.NOT_SET);
                needsSave = true;
            }
        }
        if (!profiles.containsKey(DEFAULT_PROFILE_NAME)) {
            Map<String, String> currentConfig = captureCurrentConfig();
            profiles.put(DEFAULT_PROFILE_NAME, new Profile(DEFAULT_PROFILE_NAME, currentConfig));
            needsSave = true;
        }
        if (needsSave) {
            saveProfiles();
        }
        panel.updateProfiles();
        rebuildHotkeyListeners();
    }

    Map<String, Profile> getProfiles() {
        return profiles;
    }

    @Provides
    ProfileConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ProfileConfig.class);
    }
}
