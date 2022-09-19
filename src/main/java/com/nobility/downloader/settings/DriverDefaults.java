package com.nobility.downloader.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * This is used to make switching the default driver easy.
 * Unfortunately the settings system doesn't support enums.
 * We need to use the name() function.
 * @author Nobility
 */
public enum DriverDefaults {
    CHROME, OPERA, EDGE, SAFARI, FIREFOX, CHROMIUM;

    public static DriverDefaults driverForName(String name) {
        for (DriverDefaults defaults : values()) {
            if (defaults.name().equals(name)) {
                return defaults;
            }
        }
        return CHROME;
    }

    public static boolean isChromium(DriverDefaults defaults) {
        return defaults == CHROME || defaults == CHROMIUM
                || defaults == EDGE || defaults == OPERA;
    }

    public static List<String> allDrivers() {
        List<String> drivers = new ArrayList<>();
        for (DriverDefaults defaults : values()) {
            drivers.add(defaults.name());
        }
        return drivers;
    }
}
