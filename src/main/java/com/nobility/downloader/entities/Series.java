package com.nobility.downloader.entities;

import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToMany;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;

@Entity
public class Series {

    @Id
    public long id;
    @Deprecated
    public String link;
    public String slug;
    @Deprecated
    public String movieLink;
    public String name;
    public String dateAdded;
    public ToMany<Episode> episodes = new ToMany<>(this, Series_.episodes);
    public ToMany<Genre> genres = new ToMany<>(this, Series_.genres);
    public String imageLink;
    public String description;
    public long lastUpdated;
    public int identity;
    @Transient
    public StringProperty episodeCount;

    public Series() {}

    public Series(
            String slug,
            String name,
            String imageLink,
            String description,
            String dateAdded,
            int identity
    ) {
        this.slug = slug;
        this.name = name;
        this.imageLink = imageLink;
        this.description = description;
        this.dateAdded = dateAdded;
        this.identity = identity;
    }

    public void update(Series series) {
        this.slug = series.slug;
        this.name = series.name;
        this.dateAdded = series.dateAdded;
        this.imageLink = series.imageLink;
        this.description = series.description;
        this.identity = series.identity;
        this.episodes.clear();
        this.episodes.addAll(series.episodes);
        this.genres.clear();
        this.genres.addAll(series.genres);
    }

    public void updateEpisodes(List<Episode> episodes) {
        if (this.episodes.size() != episodes.size()) {
            this.episodes.clear();
            this.episodes.addAll(episodes);
        }
    }

    public void updateEpisodes(List<Episode> episodes, boolean updateDb) {
        if (this.episodes.size() != episodes.size()) {
            this.episodes.clear();
            this.episodes.addAll(episodes);
            if (updateDb) {
                this.episodes.applyChangesToDb();
            }
        }
    }

    public void updateGenres(List<Genre> genres) {
        if (this.genres.size() != genres.size()) {
            this.genres.clear();
            this.genres.addAll(genres);
        }
    }

    public void updateSlug() {
        if (!StringChecker.isNullOrEmpty(link) && StringChecker.isNullOrEmpty(slug)) {
            this.slug = Tools.extractSlugFromLink(link);
        }
    }

    public boolean matches(Series series) {
        return series.toReadable().equals(toReadable());
    }

    public String toReadable() {
        String d = ";";
        return slug + d + name + d
                + episodes.size() + d
                + imageLink + d
                + description + d
                + genres.size() + d
                + identity;
    }

    public boolean hasImageAndDescription() {
        return !StringChecker.isNullOrEmpty(imageLink)
                && !StringChecker.isNullOrEmpty(description);
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
