package com.nobility.downloader.downloads;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nobility.downloader.scraper.Episode;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;

public class Download {

    private String downloadPath;
    private final String name;
    private final String dateAdded;
    private final String url;
    private long fileSize;
    private String seriesLink;

    //used for the session, doesn't need to be saved.
    @JsonIgnore
    private boolean downloading = false;
    @JsonIgnore
    private boolean queued = false;
    @JsonIgnore
    private final StringProperty progress;

    public Download(String downloadPath, String name, String dateAdded, String url) {
        this.downloadPath = downloadPath;
        this.name = name;
        this.dateAdded = dateAdded;
        this.url = url;
        progress = new SimpleStringProperty("0%");
    }

    public void updateProgress() {
        if (queued) {
            setProgressValue("Queued");
            return;
        }
        File video = new File(downloadPath);
        if (video.exists()) {
            double ratio = video.length() / (double) fileSize;
            setProgressValue(Tools.percentFormat.format(ratio));
        } else {
            setProgressValue("File Not Found");
        }
    }

    private void setProgressValue(String value) {
        if (!progress.getValue().equals(value)) {
            progress.setValue(value);
        }
    }

    public void update(Download download) {
        fileSize = download.fileSize;
        downloading = download.downloading;
        updateProgress();
    }

    public String getName() {
        return name;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        if (!this.downloadPath.equals(downloadPath)) {
            this.downloadPath = downloadPath;
        }
    }

    @JsonIgnore
    public File getDownloadFile() {
        if (!StringChecker.isNullOrEmpty(downloadPath)) {
            File file = new File(downloadPath);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    @JsonIgnore
    public boolean isComplete() {
        if (fileSize <= 0) {
            return false;
        }
        File file = getDownloadFile();
        if (file != null) {
            if (file.length() <= 0) {
                return false;
            }
            return file.length() >= fileSize;
        }
        return false;
    }

    public String getUrl() {
        return url;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public void setDownloading(boolean downloading) {
        this.downloading = downloading;
    }

    public void setSeriesLink(String seriesLink) {
        this.seriesLink = seriesLink;
    }

    public String getSeriesLink() {
        return seriesLink;
    }

    public String getDateAdded() {
        return dateAdded;
    }

    public StringProperty getProgress() {
        return progress;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }

    public Episode toEpisode() {
        return new Episode(name, url, seriesLink);
    }
}
