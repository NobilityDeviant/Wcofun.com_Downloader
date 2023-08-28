package com.nobility.downloader.entities;

import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class Episode {

    @Id
    public long id;
    public String name;
    @Deprecated
    public String link;
    public String slug;
    @Deprecated
    public String seriesLink;
    public String seriesSlug;
    public long lastUpdated;

    public Episode() {
    }

    public Episode(String name, String slug, String seriesSlug) {
        this.name = name;
        this.slug = slug;
        this.seriesSlug = seriesSlug;
    }

    public boolean matches(Episode episode) {
        return name.equals(episode.name);
    }

    public void updateSlugs() {
        if (!StringChecker.isNullOrEmpty(link) && StringChecker.isNullOrEmpty(slug)) {
            this.slug = Tools.extractSlugFromLink(link);
        }
        if (!StringChecker.isNullOrEmpty(seriesLink) && StringChecker.isNullOrEmpty(seriesSlug)) {
            this.seriesSlug = Tools.extractSlugFromLink(seriesLink);
        }
    }
}
