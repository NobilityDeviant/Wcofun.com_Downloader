package com.nobility.downloader.scraper;

import com.nobility.downloader.DriverBase;
import com.nobility.downloader.Model;
import com.nobility.downloader.history.SeriesHistory;
import com.nobility.downloader.utils.Tools;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;

public class ChromeDriverLinkScraper extends DriverBase implements Runnable {

    private final String url;

    public ChromeDriverLinkScraper(Model model, String url) {
        super(model);
        this.url = url;
    }

    @Override
    public void run() {
        try {
            driver.get(url);
            Document doc = Jsoup.parse(driver.getPageSource());
            Elements videoTitle = doc.getElementsByClass("video-title");
            Elements elements = doc.getElementsByClass("cat-eps");
            if (!elements.isEmpty()) {
                Collections.reverse(elements);
                for (Element element : elements) {
                    Elements href = element.getElementsByAttribute("href");
                    String s = href.toString();
                    String index1 = "<a href=\"";
                    try {
                        Episode episode = new Episode(href.html().trim().replaceAll("[\\\\/*?\"<>|]", "_").replaceAll(":", ";"),
                                s.substring(s.indexOf(index1) + index1.length(), s.indexOf("\" rel=\"bookmark\"")).trim(), url);
                        model.getLinks().add(episode);
                        model.incrementLinksFound();
                    } catch (Exception ignored) {
                        System.out.println("Unable to find link for " + href.html());
                    }
                }
                System.out.println("Found " + model.getLinksFound() + " episodes from " + url);
                Elements titleElement = doc.getElementsByClass("h1-tag");
                String title = "";
                if (!titleElement.isEmpty()) {
                    title = titleElement.get(0).text();
                }
                //todo save all links in history
                model.getHistorySave().addSeries(new SeriesHistory(url, title,
                        elements.size(), Tools.getDateFormatted()), false);
                model.saveSeriesHistory();
            } else {
                //todo save series even if it's just an episode
                Elements catgeory = doc.getElementsByClass("header-tag");
                String seriesLink = "";
                if (!catgeory.isEmpty()) {
                    String html = catgeory.get(0).html();
                    String index = "<a href=\"";
                    seriesLink = html.substring(html.indexOf(index) + index.length(), html.indexOf("\" rel=\""));
                }
                if (!videoTitle.isEmpty()) {
                    model.getLinks().add(new Episode(videoTitle.get(0).text().trim()
                            .replaceAll("[\\\\/*?\"<>|]", "_")
                            .replaceAll(":", ";"), url, seriesLink));
                    model.incrementLinksFound();
                    System.out.println("Found episode " + videoTitle.get(0).text().trim()
                            .replaceAll("[\\\\/*?\"<>|]", "_")
                            .replaceAll(":", ";")
                            + " " + (seriesLink.length() > 0 ? "| Category: " + seriesLink : ""));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to grab episode links for: " + url + " Error: " + e.getLocalizedMessage());
        } finally {
            killDriver();
        }
    }
}
