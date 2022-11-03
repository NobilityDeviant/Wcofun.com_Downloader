package com.nobility.downloader.entities;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToMany;

@Entity
public class Genre {

    @Id
    public long id;
    public String name;
    public String link;
    @Backlink(to = "genres")
    public ToMany<Series> series;
    public long lastUpdated;

    public Genre() {}

    public Genre(String name, String link) {
        this.name = name;
        this.link = link;
    }

}
