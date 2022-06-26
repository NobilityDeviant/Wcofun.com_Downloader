package com.nobility.downloader.scraper;

import com.nobility.downloader.Model;
import com.nobility.downloader.history.SeriesHistory;
import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Collections;

public class JSoupLinkScraper implements Runnable {

    private final Model model;
    private final String url;

    public JSoupLinkScraper(Model model, String url) {
        this.model = model;
        this.url = url;
    }

    @Override
    public void run() {
        try {
            Document doc;
            if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
                String[] proxy = model.settings().getString(Defaults.PROXY).split(":");
                doc = Jsoup.connect(url).proxy(proxy[0], Integer.parseInt(proxy[1]))
                        .userAgent(model.getRandomUserAgent()).timeout(model.settings()
                                .getInteger(Defaults.PROXYTIMEOUT) * 1000).get();
            } else {
                doc = Jsoup.connect(url).userAgent(model.getRandomUserAgent()).timeout(model
                        .settings().getInteger(Defaults.PROXYTIMEOUT) * 1000).get();
            }
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
                model.getHistorySave().addSeries(new SeriesHistory(url, title,
                        elements.size(), Tools.getDateFormatted()), false);
                model.saveSeriesHistory();
            } else {
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
        } catch (IOException e) {
            System.err.println("Failed to grab episode links for: " + url + " Error: " + e.getLocalizedMessage());
        }
    }
}
