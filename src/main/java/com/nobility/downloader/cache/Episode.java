package com.nobility.downloader.cache;

import java.io.Serializable;

public class Episode implements Serializable {

    private final String name;
    private final String link;
    private final String seriesLink;

    public Episode(String name, String link, String seriesLink) {
        this.name = name;
        this.link = link;
        this.seriesLink = seriesLink;
    }

    public String getLink() {
        return link;
    }

    public String getName() {
        return name;
    }

    public String getSeriesLink() {
        return seriesLink;
    }

    @Override
    public String toString() {
        return "(Name: " + name + " Link: " + link + ")";
    }
}
