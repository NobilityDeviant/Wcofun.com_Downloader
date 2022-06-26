package com.nobility.downloader.series;

import com.nobility.downloader.DriverBase;
import com.nobility.downloader.Model;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Locale;

public class SeriesDetailsScraper extends DriverBase implements Runnable {

    private final List<String> links;

    public SeriesDetailsScraper(Model model, List<String> links) {
        super(model);
        this.links = links;
    }

    @Override
    public void run() {
        System.out.println("Searching through " + links.size() + " on a thread.");
        for (String s: links) {
            driver.get(s);
            SeriesDetails details = new SeriesDetails();
            details.setUrl(s);
            Document doc = Jsoup.parse(driver.getPageSource());
            Elements loaded = doc.getElementsByClass("recent-release");
            if (loaded.text().toLowerCase(Locale.ROOT).contains("page not found")) {
                System.err.println("Not found");
                killDriver();
                continue;
            }
            Elements image = doc.getElementsByClass("img5");
            if (!image.isEmpty()) {
                try {
                    String outer = image.get(0).outerHtml();
                    String key1 = "src=\"//";
                    String key2 = "\" alt=\"\">";
                    String url = "https://" + outer.substring(outer.indexOf(key1) + key1.length(), outer.indexOf(key2));
                    details.setImageUrl(url);
                } catch (Exception e) {
                    e.printStackTrace();
                    killDriver();
                    continue;
                }
            }
            Elements description = doc.getElementsByTag("p");
            if (!description.isEmpty()) {
                details.setDescription(description.get(0).text());
            } else {
                System.err.println("Desc not found");
                killDriver();
                continue;
            }
            Elements title = doc.getElementsByClass("h1-tag");
            if (!title.isEmpty()) {
                details.setTitle(title.get(0).text());
            } else {
                System.err.println("Title not found");
                killDriver();
                continue;
            }
            model.details.add(details);
            System.err.println("Scraped: " + details.getTitle());
        }
        killDriver();
    }
}
