package com.pluginimporter;

import net.runelite.client.config.Keybind;
import java.util.Map;

public class Profile {
    private String name;
    private Map<String, String> config;
    private String area;
    private Keybind hotkey;

    public Profile(String name, Map<String, String> config) {
        this.name = name;
        this.config = config;
        this.hotkey = Keybind.NOT_SET;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public Keybind getHotkey() {
        return hotkey;
    }

    public void setHotkey(Keybind hotkey) {
        this.hotkey = hotkey;
    }
}
