package com.nobility.downloader.scraper;

import com.nobility.downloader.Model;
import com.nobility.downloader.history.SeriesHistory;
import com.nobility.downloader.scraper.settings.Defaults;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ChromeDriverLinkScraper implements Runnable {

    private final ChromeOptions options = new ChromeOptions();
    private WebDriver driver = null;
    private final Model model;
    private final String url;

    public ChromeDriverLinkScraper(Model model, String url) {
        this.model = model;
        this.url = url;
        setupDriver();
    }

    private void setupDriver() {
        options.setHeadless(true);
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments("--no-sandbox"); //https://stackoverflow.com/a/50725918/1689770
        options.addArguments("--disable-infobars"); //https://stackoverflow.com/a/43840128/1689770
        options.addArguments("--disable-dev-shm-usage"); //https://stackoverflow.com/a/50725918/1689770
        options.addArguments("--disable-browser-side-navigation"); //https://stackoverflow.com/a/49123152/1689770
        options.addArguments("--disable-gpu");
        options.addArguments("enable-automation");
        options.addArguments("--mute-audio");
        options.addArguments("user-agent=" + model.getRandomUserAgent());
        if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
            options.addArguments("--proxy-server=" + model.settings().getString(Defaults.PROXY));
        }
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT), TimeUnit.SECONDS);
        driver.manage().timeouts().pageLoadTimeout(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT), TimeUnit.SECONDS);
        driver.manage().timeouts().setScriptTimeout(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT), TimeUnit.SECONDS);
        model.getRunningDrivers().add(driver);
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
        } catch (Exception e) {
            System.err.println("Failed to grab episode links for: " + url + " Error: " + e.getLocalizedMessage());
        } finally {
            if (driver != null) {
                model.getRunningDrivers().remove(driver);
                driver.close();
                driver.quit();
            }
        }
    }
}
