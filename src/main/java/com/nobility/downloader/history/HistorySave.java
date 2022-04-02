package com.nobility.downloader.history;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class HistorySave {

    private final List<SeriesHistory> savedSeries = new ArrayList<>();

    public List<SeriesHistory> getSavedSeries() {
        return savedSeries;
    }

    @JsonIgnore
    public void addSeries(SeriesHistory seriesHistory, boolean replace) {
        if (getSeriesHistoryByLink(seriesHistory.getSeriesLink()) == null) {
            savedSeries.add(seriesHistory);
        } else {
            if (replace) {
                replaceSeries(seriesHistory);
            }
        }
    }

    @JsonIgnore
    public void replaceSeries(SeriesHistory seriesHistory) {
        int i = 0;
        for (SeriesHistory history : savedSeries) {
            if (history.getSeriesLink().equals(seriesHistory.getSeriesLink())) {
                savedSeries.remove(i);
                savedSeries.add(seriesHistory);
            }
            i++;
        }
    }

    @JsonIgnore
    public void removeSeries(SeriesHistory seriesHistory) {
        savedSeries.remove(seriesHistory);
    }

    @JsonIgnore
    public SeriesHistory getSeriesHistoryByLink(String link) {
        for (SeriesHistory history : savedSeries) {
            if (history.getSeriesLink().equals(link)) {
                return history;
            }
        }
        return null;
    }
}
