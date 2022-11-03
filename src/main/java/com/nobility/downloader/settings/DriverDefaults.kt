package com.nobility.downloader.settings

/**
 * This is used to make switching the default driver easy.
 * Unfortunately the settings system doesn't support enums.
 * We need to use the name() function.
 * @author Nobility
 */
enum class DriverDefaults {
    CHROME, OPERA, EDGE, SAFARI, FIREFOX, CHROMIUM;

    companion object {
        fun driverForName(name: String): DriverDefaults {
            for (defaults in values()) {
                if (defaults.name == name) {
                    return defaults
                }
            }
            return CHROME
        }

        fun isChromium(defaults: DriverDefaults): Boolean {
            return defaults == CHROME || defaults == CHROMIUM || defaults == EDGE || defaults == OPERA
        }

        fun allDrivers(): List<String> {
            val drivers: MutableList<String> = ArrayList()
            for (defaults in values()) {
                drivers.add(defaults.name)
            }
            return drivers
        }
    }
}