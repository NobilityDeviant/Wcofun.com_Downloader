package com.nobility.downloader.scraper;

import java.io.File;
import java.io.Serializable;

public class Episode implements Serializable {

    private final String name;
    private final String link;
    private final String seriesLink;
    private Integer season;

    public Episode(String name, String link, String seriesLink) {
        this.name = name;
        this.link = link;
        this.seriesLink = seriesLink;
        /*
        want to find seasons but some seasons have 2 digits
        we cant default to season 1 folder because it has no season in the name..
        scrapping the idea for now
         */
        /*String nameToLowercase = name.toLowerCase(Locale.ROOT);
        String key = "season";
        if (nameToLowercase.contains(key)) {
            String number = nameToLowercase.substring(nameToLowercase
                    .indexOf(key) + key.length() + 1, nameToLowercase.indexOf(key) + key.length() + 2);
            try {
                season = Integer.parseInt(number);
            } catch (Exception ignored) {
                //e.printStackTrace();
            }
        }*/
    }

    public String getLink() {
        return link;
    }

    public String getName() {
        return name;
    }

    public String getSeriesLink() {
        return seriesLink;
    }

    public String getSeasonFolder() {
        if (season != null) {
            return "Season " + season + File.separator;
        } else {
            return "";
        }
    }

    @Override
    public String toString() {
        return "(Name: " + name + " Link: " + link + ")";
    }
}
