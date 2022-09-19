package com.nobility.downloader.settings;

/**
 * An enum used for easy settings management.
 * The type is used to identify the type for the setting. This makes loading them faster.
 * The value is an object that is identified by the type.
 * 0 is for booleans
 * 1 is for integers
 * 2 is for strings
 */
public enum Defaults {

    DENIEDUPDATE("deniedupdate", 0, false),
    UPDATEVERSION("version", 2, "1.0"),
    PROXY("proxy", 2, ""),
    TIMEOUT("timeout", 1, 30),
    ENABLEPROXY("enable_proxy", 0, false),
    SAVELINKS("save_links", 0, false),
    SHOWCONTEXTONCLICK("show_context", 0, true),
    MAXEPISODES("max_episodes", 1, 30),
    NOEPISODELIMIT("no_episode_limit", 0, false),
    SILENTDRIVER("silent_driver", 0, true),
    SAVEFOLDER("save_folder", 2, System.getProperty("user.home")),
    DOWNLOADTHREADS("download_threads", 1, 2),
    LASTDOWNLOAD("last_dl", 2, ""),
    DRIVER("driver", 2, DriverDefaults.CHROME.name());

    private final String key;
    private final int type; //0 == boolean, 1 == int, 2 == string
    private final Object value;

    Defaults(String key, int type, Object value) {
        this.key = key;
        this.type = type;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public int getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

}
