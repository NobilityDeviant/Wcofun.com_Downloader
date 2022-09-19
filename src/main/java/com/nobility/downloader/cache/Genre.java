package com.nobility.downloader.cache;

public class Genre {

    private final String name;
    private final String link;

    public Genre(String name, String link) {
        this.name = name;
        this.link = link;
    }

    public String getLink() {
        return link;
    }

    public String getName() {
        return name;
    }
}
