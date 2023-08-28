package com.nobility.downloader.entities;

import com.nobility.downloader.settings.Quality;
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
    public int resolution;
    @Transient
    public boolean downloading;
    @Transient
    public boolean queued;
    @Transient
    public StringProperty progress;
    @Transient
    public StringProperty fileSizeProperty;
    @Transient
    public StringProperty dateProperty;

    public Download() {
        progress = new SimpleStringProperty("0%");
        fileSizeProperty = new SimpleStringProperty("0KB");
        dateProperty = new SimpleStringProperty("");
    }

    public Download(
            String downloadPath,
            String name,
            String slug,
            String seriesSlug,
            int resolution,
            long fileSize,
            long dateAdded
    ) {
        this.downloadPath = downloadPath;
        this.name = name;
        this.slug = slug;
        this.seriesSlug = seriesSlug;
        this.resolution = resolution;
        this.fileSize = fileSize;
        this.dateAdded = dateAdded;
        progress = new SimpleStringProperty("0%");
        fileSizeProperty = new SimpleStringProperty("0KB");
        dateProperty = new SimpleStringProperty("");
    }

    public void update(Download download, boolean updateProperties) {
        this.downloadPath = download.downloadPath;
        this.dateAdded = download.dateAdded;
        this.fileSize = download.fileSize;
        this.queued = download.queued;
        this.downloading = download.downloading;
        this.resolution = download.resolution;
        if (updateProperties) {
            updateFileSizeProperty();
            updateProgress();
            updateDateProperty();
        }
    }

    public boolean matches(Download download) {
        return (download.id > 0 && download.id == id)
                || (download.slug.equals(slug) && resolution == download.resolution);
    }

    public void updateFileSizeProperty() {
        fileSizeProperty.setValue(Tools.bytesToString(fileSize));

    }

    public void updateDateProperty() {
        dateProperty.setValue(Tools.dateFormatted(dateAdded));
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

    public String nameAndResolution() {
        Quality quality = Quality.Companion.qualityForResolution(resolution);
        String extra = quality == Quality.LOW ? "" : (" (" + quality.getTag() + ")");
        return name + extra;
    }

}
