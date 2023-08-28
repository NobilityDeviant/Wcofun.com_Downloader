package com.nobility.downloader.entities;

import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToMany;

@Entity
public class Genre {

    @Id
    public long id;
    public String name;
    @Deprecated
    public String link;
    public String slug;
    @Backlink(to = "genres")
    public ToMany<Series> series = new ToMany<>(this, Genre_.series);
    public long lastUpdated;

    public Genre() {}

    public Genre(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public void updateSlug() {
        if (!StringChecker.isNullOrEmpty(link) && StringChecker.isNullOrEmpty(slug)) {
            this.slug = Tools.extractSlugFromLink(link);
        }
    }

}
