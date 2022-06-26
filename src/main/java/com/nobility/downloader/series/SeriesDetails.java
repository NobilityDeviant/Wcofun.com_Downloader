package com.nobility.downloader.series;

public class SeriesDetails {

    private String title;
    private String description;
    private String imageUrl;
    private String url;

    public SeriesDetails() {}

    public SeriesDetails(String title, String description, String imageUrl) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "[Series] Title: " + title + " Description: " + description + " URL: " + imageUrl;
    }
}
