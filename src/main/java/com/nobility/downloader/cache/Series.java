package com.nobility.downloader.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;

import java.util.List;

public class Series {

    private final String link;
    private final String name;
    private final String dateAdded;
    private final List<Episode> episodes;
    private final String imageLink;
    private final String description;
    private final List<Genre> genres;

    @JsonIgnore
    public String toReadable() {
        String d = ";";
        return link + d
                + name + d
                + episodes + d
                + imageLink + d
                + description + d
                + genres;
    }

    public Series(
            String link,
            String name,
            String imageLink,
            String description,
            List<Episode> episodes,
            List<Genre> genres,
            String dateAdded
    ) {
        this.link = link;
        this.name = name;
        this.imageLink = imageLink;
        this.description = description;
        this.episodes = episodes;
        this.genres = genres;
        this.dateAdded = dateAdded;
    }

    public String getLink() {
        return link;
    }

    public String getName() {
        return name;
    }

    public String getImageLink() {
        return imageLink;
    }

    public String getDescription() {
        return description;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public String getDateAdded() {
        return dateAdded;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    @JsonIgnore
    public boolean hasImageAndDescription() {
        return !StringChecker.isNullOrEmpty(imageLink)
                && !StringChecker.isNullOrEmpty(description);
    }

    @JsonIgnore
    public boolean hasEpisode(Episode episode) {
        if (episode == null) {
            return false;
        }
        for (Episode e : episodes) {
            if (Tools.fixOldLink(e.getLink())
                    .equals(Tools.fixOldLink(episode.getLink()))) {
                return true;
            }
        }
        return false;
    }
}
