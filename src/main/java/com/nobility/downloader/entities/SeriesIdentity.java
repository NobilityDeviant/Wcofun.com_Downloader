package com.nobility.downloader.entities;

public enum SeriesIdentity {

    DUBBED("dubbed-anime-list", 0),
    SUBBED("subbed-anime-list", 1),
    CARTOON("cartoon-list", 2),
    MOVIE("movie-list", 3),
    NONE("404", 4);

    private final int type;
    private final String link;

    public int getType() {
        return type;
    }

    public String getLink() {
        return link;
    }

    public static SeriesIdentity idForType(int type) {
        for (SeriesIdentity id : values()) {
            if (id.type == type) {
                return id;
            }
        }
        return NONE;
    }

    SeriesIdentity(String link, int type) {
        this.type = type;
        this.link = link;
    }

}
