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
    @JsonIgnore
    private final StringProperty nameProperty;
    @JsonIgnore
    private final StringProperty dateAddedProperty;
    private final String url;
    private final String progress;
    @JsonIgnore
    private StringProperty progressProperty;
    private long fileSize;
    private boolean downloading = false;
    private String seriesLink;

    public Episode toEpisode() {
        return new Episode(nameProperty.getValue(), url, seriesLink);
    }

    public Download(String downloadPath, String name, String dateAdded, String url, String progress) {
        this.downloadPath = downloadPath;
        this.name = name;
        nameProperty = new SimpleStringProperty(this, "name");
        setNameProperty(name);
        this.dateAdded = dateAdded;
        dateAddedProperty = new SimpleStringProperty(this, "date");
        setDateAddedProperty(dateAdded);
        this.url = url;
        this.progress = progress;
        progressProperty = new SimpleStringProperty(this, "progress");
    }

    //progressProperty is not serializable, so we have to update it when the file is loaded
    public void updateSerializedProgress() {
        if (progressProperty == null) {
            progressProperty = new SimpleStringProperty(this, "progress");
        }
        File video = new File(downloadPath);
        if (video.exists()) {
            double ratio = video.length() / (double) fileSize;
            progressProperty.setValue(Tools.percentFormat.format(ratio));
            //TODO on launch it seemed to have set the ratio to a negative long? only 1 episode
        } else {
            progressProperty.setValue("Error");
        }
        downloading = false;
    }

    public void update(Download download) {
        progressProperty = download.progressProperty;
        fileSize = download.fileSize;
        downloading = download.downloading;
    }

    @JsonIgnore
    public StringProperty getProgressProperty() {
        return progressProperty;
    }

    @JsonIgnore
    public void setProgressProperty(String progress) {
        progressProperty.setValue(progress);
    }

    public void setNameProperty(String name) {
        nameProperty.setValue(name);
    }

    public void setDateAddedProperty(String date) {
        dateAddedProperty.setValue(date);
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    public StringProperty getNameProperty() {
        return nameProperty;
    }

    public String getDateAdded() {
        return dateAdded;
    }

    public String getProgress() {
        return progress;
    }

    @JsonIgnore
    public StringProperty getDateAddedProperty() {
        return dateAddedProperty;
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
        File file = getDownloadFile();
        if (fileSize == 0 || Tools.bytesToMB(fileSize) <= 3.0) {
            return false;
        }
        if (file != null) {
            if (file.length() == 0) {
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
}
