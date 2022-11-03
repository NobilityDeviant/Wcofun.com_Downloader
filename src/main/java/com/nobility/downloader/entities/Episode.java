package com.nobility.downloader.entities;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class Episode {

    @Id
    public long id;
    public String name;
    public String link;
    public String seriesLink;
    public long lastUpdated;

    public Episode() {}

    public Episode(String name, String link, String seriesLink) {
        this.name = name;
        this.link = link;
        this.seriesLink = seriesLink;
    }

    public boolean matches(Episode episode) {
        return name.equals(episode.name);
    }
}
