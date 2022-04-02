package com.nobility.downloader.scraper.settings;

public enum Defaults {

    DENIEDUPDATE("deniedupdate", 0, false),
    UPDATEVERSION("version", 2, "1.0"),
    PROXY("proxy", 2, ""),
    PROXYTIMEOUT("proxy_timeout", 1, 15),
    SAVELINKS("save_links", 0, false),
    MAXLINKS("max_links", 1, 30),
    SILENTDRIVER("silent_driver", 0, true),
    SAVEFOLDER("save_folder", 2, System.getProperty("user.home")),
    THREADS("threads", 1, 2),
    LASTDOWNLOAD("last_dl", 2, "");

    private final String key;
    private final int type; //0 - bool, 1 == int, 2 == string
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
