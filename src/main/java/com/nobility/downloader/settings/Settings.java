package com.nobility.downloader.settings;

import java.util.HashMap;
import java.util.Map;

public class Settings {

    private final Map<String, Integer> integers = new HashMap<>();
    private final Map<String, Boolean> booleans = new HashMap<>();
    private final Map<String, String> strings = new HashMap<>();

    public void loadDefaultSettings() {
        for (Defaults setting : Defaults.values()) {
            switch (setting.getType()) {
                case 0:
                    setBoolean(setting, (Boolean) setting.getValue());
                    break;
                case 1:
                    setInteger(setting, (Integer) setting.getValue());
                    break;
                case 2:
                    setString(setting, (String) setting.getValue());
                    break;
            }
        }
    }

    public void checkForNewSettings() {
        for (Defaults setting : Defaults.values()) {
            switch (setting.getType()) {
                case 0:
                    if (!booleans.containsKey(setting.getKey())) {
                        booleans.put(setting.getKey(), (Boolean) setting.getValue());
                    }
                    break;
                case 1:
                    if (!integers.containsKey(setting.getKey())) {
                        integers.put(setting.getKey(), (Integer) setting.getValue());
                    }
                    break;
                case 2:
                    if (!strings.containsKey(setting.getKey())) {
                        strings.put(setting.getKey(), (String) setting.getValue());
                    }
                    break;
            }
        }
    }

    public boolean getBoolean(Defaults settings) {
        return booleans.getOrDefault(settings.getKey(), false);
    }

    public int getInteger(Defaults settings) {
        return integers.getOrDefault(settings.getKey(), 0);
    }

    public String getString(Defaults settings) {
        return strings.getOrDefault(settings.getKey(), "");
    }

    public void setBoolean(Defaults settings, boolean value) {
        if (booleans.containsKey(settings.getKey())) {
            booleans.replace(settings.getKey(), value);
        } else {
            booleans.put(settings.getKey(), value);
        }
    }

    public void setInteger(Defaults settings, int value) {
        if (integers.containsKey(settings.getKey())) {
            integers.replace(settings.getKey(), value);
        } else {
            integers.put(settings.getKey(), value);
        }
    }

    public void setString(Defaults settings, String value) {
        if (strings.containsKey(settings.getKey())) {
            strings.replace(settings.getKey(), value);
        } else {
            strings.put(settings.getKey(), value);
        }
    }
}
