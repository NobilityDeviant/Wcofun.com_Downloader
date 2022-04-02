package com.nobility.downloader.history;

public class SeriesHistory {

    private final String seriesLink;
    private final String seriesName;
    private final int episodes;
    private final String dateAdded;

    public SeriesHistory(String seriesLink, String seriesName, int episodes, String dateAdded) {
        this.seriesLink = seriesLink;
        this.seriesName = seriesName;
        this.episodes = episodes;
        this.dateAdded = dateAdded;
    }

    public String getSeriesLink() {
        return seriesLink;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public int getEpisodes() {
        return episodes;
    }

    public String getDateAdded() {
        return dateAdded;
    }
}
