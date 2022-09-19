package com.nobility.downloader.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nobility.downloader.cache.Series;

import java.util.ArrayList;
import java.util.List;

public class History {

    private final List<Series> savedSeries = new ArrayList<>();

    public List<Series> getSavedSeries() {
        return savedSeries;
    }

    @JsonIgnore
    public boolean addSeries(Series series, boolean replace) {
        if (seriesHistoryForLink(series.getLink()) == null) {
            savedSeries.add(series);
            return true;
        } else {
            if (replace) {
                return replaceSeries(series);
            }
        }
        return false;
    }

    @JsonIgnore
    private boolean replaceSeries(Series series) {
        int i = 0;
        for (Series history : new ArrayList<>(savedSeries)) {
            if (history.getLink().equals(series.getLink())) {
                if (!areSeriesEqual(history, series)) {
                    savedSeries.remove(i);
                    savedSeries.add(series);
                    return true;
                }
                break;
            }
            i++;
        }
        return false;
    }

    @JsonIgnore
    private boolean areSeriesEqual(Series history1, Series history2) {
        return history1.toReadable().equals(history2.toReadable());
    }

    @JsonIgnore
    public void removeSeries(Series series) {
        savedSeries.remove(series);
    }

    @JsonIgnore
    public Series seriesHistoryForLink(String link) {
        for (Series history : savedSeries) {
            if (history.getLink().equals(link)) {
                return history;
            }
        }
        return null;
    }
}
