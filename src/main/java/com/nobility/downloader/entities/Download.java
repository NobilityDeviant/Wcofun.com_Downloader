package com.nobility.downloader.entities;

import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Transient;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;

@Entity
public class Download extends Episode {

    public String downloadPath;
    public long dateAdded;
    public long fileSize;

    @Transient
    public boolean downloading;
    @Transient
    public boolean queued;
    @Transient
    public StringProperty progress;
    @Transient
    public StringProperty fileSizeProperty;

    public Download() {
        progress = new SimpleStringProperty("0%");
        fileSizeProperty = new SimpleStringProperty("0KB");
    }

    public Download(
            String downloadPath,
            String name,
            String link,
            String seriesLink,
            long fileSize,
            long dateAdded
    ) {
        this.downloadPath = downloadPath;
        this.name = name;
        this.link = link;
        this.seriesLink = seriesLink;
        this.fileSize = fileSize;
        this.dateAdded = dateAdded;
        progress = new SimpleStringProperty("0%");
        fileSizeProperty = new SimpleStringProperty("0KB");
    }

    public void update(Download download, boolean updateProperties) {
        this.downloadPath = download.downloadPath;
        this.dateAdded = download.dateAdded;
        this.fileSize = download.fileSize;
        this.queued = download.queued;
        this.downloading = download.downloading;
        if (updateProperties) {
            updateFileSizeProperty();
            updateProgress();
        }
    }

    public boolean matches(Download download) {
        return (download.id > 0 && download.id == id) || Tools.fixOldLink(download.link)
                .equals(Tools.fixOldLink(link));
    }

    public void updateFileSizeProperty() {
        fileSizeProperty.setValue(Tools.bytesToString(fileSize));
    }

    public void updateProgress() {
        if (queued) {
            setProgressValue("Queued");
            return;
        }
        File video = downloadFile();
        if (video != null && video.exists()) {
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

    public File downloadFile() {
        if (!StringChecker.isNullOrEmpty(downloadPath)) {
            File file = new File(downloadPath);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }


    public boolean isComplete() {
        if (fileSize <= 5000) {
            return false;
        }
        File file = downloadFile();
        if (file != null) {
            if (file.length() <= 0) {
                return false;
            } else {
                return file.length() >= fileSize;
            }
        }
        return false;
    }

}
