package com.nobility.downloader.series;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import com.google.common.collect.Lists;
import com.nobility.downloader.DriverBase;
import com.nobility.downloader.Model;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//todo scrape all categories
//organize them and divide them into lists
//move the start method to model so we can launch it outside the main controller
//add pause video
//add queued downloads with a progress of "Waiting..."
//add a way to add downloads to the queue while it's downloading
//add a way to download mutliple urls
public class SeriesScaper extends DriverBase {

    private final String dubbedSeries = "https://www.wcofun.com/dubbed-anime-list";
    private final String subbedSeries = "https://www.wcofun.com/subbed-anime-list";
    private final String cartoonSeries = "https://www.wcofun.com/cartoon-list";
    private List<String> links = new ArrayList<>();

    public SeriesScaper(Model model) {
        super(model);
    }

    public void scrapeSeriesIntoFile() {
        driver.get(dubbedSeries);
        Document doc = Jsoup.parse(driver.getPageSource());
        Elements list = doc.getElementsByClass("ddmcc");
        Elements ul = list.select("ul");
        for (Element uls : ul) {
            Elements lis = uls.select("li");
            for (Element li : lis)
                links.add(li.select("a").attr("href"));
        }
        killDriver();
        if (links.isEmpty()) {
            System.out.println("Failed to find any links for: " + dubbedSeries);
            return;
        }
        links = links.subList(0, 6);
        System.out.println("Successfully found " + links.size() + " links for dubbed.");
        int threads = 5;
        System.out.println("Running 5 parallel threads (split by " + links.size() / threads + ") and scraping each series details. This might take awhile...");
        List<List<String>> subLists = Lists.partition(links, links.size() / threads);
        ExecutorService service = Executors.newFixedThreadPool(subLists.size());
        for (List<String> subList : subLists) {
            service.submit(new SeriesDetailsScraper(model, subList));
        }
        try {
            service.shutdown();
            if (service.awaitTermination(120, TimeUnit.MINUTES)) {
                System.out.println("Successfully found " + model.details.size() + " series.");
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new ParanamerModule());
                mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
                mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
                mapper.setDefaultPrettyPrinter(new MinimalPrettyPrinter());
                try {
                    mapper.writeValue(new File("./dubbed.txt"), model.details);
                    model.openFile(new File("./dubbed.txt"), false);
                    System.out.println("Successfully saved dubbed series.");
                } catch (IOException e) {
                    System.err.println("Failed to save dubbed series. Error: " + e.getLocalizedMessage());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            service.shutdownNow();
        }
    }
}
