package com.nobility.downloader.entities;

import java.util.Arrays;
import java.util.List;

public enum SeriesIdentity {

    DUBBED("dubbed-anime-list", 0),
    SUBBED("subbed-anime-list", 1),
    CARTOON("cartoon-list", 2),
    MOVIE("movie-list", 3),
    NEW("404", 4),
    NONE("404", 5);

    private final int type;
    private final String link;

    public int getType() {
        return type;
    }

    public String getLink() {
        return link;
    }

    public static List<SeriesIdentity> filteredValues() {
        return Arrays.asList(DUBBED, SUBBED, CARTOON, MOVIE);
    }

    public static SeriesIdentity idForType(int type) {
        for (SeriesIdentity id : values()) {
            if (id.type == type) {
                return id;
            }
        }
        return NONE;
    }

    public static boolean isFilteredType(int type) {
        return idForType(type) == NONE || idForType(type) == NEW;
    }

    SeriesIdentity(String link, int type) {
        this.type = type;
        this.link = link;
    }

}
