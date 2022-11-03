package com.nobility.downloader.entities;

import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToMany;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

@Entity
public class Series {

    @Id
    public long id;
    public String link;

    public String movieLink;
    public String name;
    public String dateAdded;
    public ToMany<Episode> episodes;
    public ToMany<Genre> genres;
    public String imageLink;
    public String description;
    public long lastUpdated;
    public int identity;
    @Transient
    public StringProperty episodeCount;

    public boolean matches(Series series) {
        return series.toReadable().equals(toReadable());
    }

    public String toReadable() {
        String d = ";";
        return link + d
                + name + d
                + episodes.size() + d
                + imageLink + d
                + description + d
                + genres.size() + d
                + identity;
    }

    public String toPrintable() {
        String d = ";";
        return link + d + name + d + episodes.size() + d + id;
    }

    public Series() {}

    public Series(
            String link,
            String name,
            String imageLink,
            String description,
            String dateAdded,
            int identity
    ) {
        this.link = link;
        this.name = name;
        this.imageLink = imageLink;
        this.description = description;
        this.dateAdded = dateAdded;
        this.identity = identity;
    }

    public boolean hasImageAndDescription() {
        return !StringChecker.isNullOrEmpty(imageLink)
                && !StringChecker.isNullOrEmpty(description);
    }

    public boolean hasEpisode(Episode episode) {
        if (episode == null) {
            return false;
        }
        for (Episode e : episodes) {
            if (Tools.fixOldLink(e.link)
                    .equals(Tools.fixOldLink(episode.link))) {
                return true;
            }
        }
        return false;
    }

    public Episode episodeForLink(String link) {
        for (Episode e : episodes) {
            if (Tools.fixOldLink(e.link)
                    .equals(Tools.fixOldLink(link))) {
                return e;
            }
        }
        return null;
    }

    public void updateEpisodeCountValue() {
        episodesCountProperty().setValue(String.valueOf(episodes.size()));
    }

    public StringProperty episodesCountProperty() {
        if (episodeCount == null) {
            episodeCount = new SimpleStringProperty("");
        }
        return episodeCount;
    }
}
