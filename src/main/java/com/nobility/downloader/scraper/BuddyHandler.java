package com.nobility.downloader.scraper;

import com.nobility.downloader.Model;
import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.AlertBox;
import javafx.scene.control.Alert;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BuddyHandler {

    private final Model model;
    public String url;

    public BuddyHandler(Model model) {
        this.model = model;
    }

    public void update(String url, boolean chrome) throws Exception {
        this.url = url;
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(chrome ? new ChromeDriverLinkScraper(model, url) : new JSoupLinkScraper(model, url));
        service.shutdown();
        if (service.awaitTermination(2, TimeUnit.MINUTES)) {
            if (!model.getLinks().isEmpty()) {
                System.out.println("Launching downloader for " + url + " Episodes: " + model.getLinks().size());
            } else {
                if (!chrome) {
                    throw new Exception("Failed to use Jsoup. Using Chrome Driver instead.");
                }
            }
        }
    }

    public void launch() {
        model.start();
        String saveFolder = "";
        if (!model.getLinks().isEmpty()) {
            String name = model.getLinks().get(0).getName();
            if (name.toLowerCase(Locale.US).contains("episode")) {
                saveFolder = name.substring(0, name.toLowerCase(Locale.US).indexOf("episode")).trim();
                        //.replaceAll("[^\\x00-\\x7F]", "").replaceAll(" ", "_");
            } else {
                saveFolder = name;
            }
        }
        String finalSaveFolder = saveFolder;
        new Thread(() -> {
            File saveDir = new File(model.settings().getString(Defaults.SAVEFOLDER));
            if (!saveDir.exists()) {
                if (!saveDir.mkdir()) {
                    AlertBox.show(Alert.AlertType.ERROR, "Your download folder doesn't exist.", "Be sure to set it inside " +
                            "the settings before downloading videos.");
                    model.stop();
                    return;
                }
            }
            File outputDir = new File(saveDir.getAbsolutePath() + File.separator + finalSaveFolder);
            if (!outputDir.exists()) {
                if (!outputDir.mkdir()) {
                    AlertBox.show(Alert.AlertType.ERROR, "Unable to create series folder.",
                            saveDir.getAbsolutePath() + File.separator + finalSaveFolder + " was unable to be created.");
                    model.stop();
                    return;
                }
            }
            if (model.settings().getBoolean(Defaults.SAVELINKS)) {
                try {
                    BufferedWriter w = new BufferedWriter(new FileWriter(outputDir.getAbsolutePath()
                            + File.separator + "links.txt"));
                    for (Episode episode : model.getLinks()) {
                        w.write(episode.getLink());
                        w.newLine();
                    }
                    w.flush();
                    w.close();
                    System.out.println("Successfully saved the links to: " + outputDir.getAbsolutePath()
                            + File.separator + "links.txt");
                } catch (Exception e) {
                    System.out.println("Failed to save links. Error: " + e.getLocalizedMessage());
                    //model.stop();
                    //return;
                }
            }
            try {
                int threads = model.settings().getInteger(Defaults.THREADS);
                if (model.getLinks().size() < threads) {
                    threads = model.getLinks().size();
                }
                ExecutorService service = Executors.newFixedThreadPool(threads);
                for (int i = 0; i < threads; i++) {
                    service.submit(new VideoDownloader(model, outputDir.getAbsolutePath()));
                }
                service.shutdown();
                if (service.awaitTermination(12, TimeUnit.HOURS)) {
                    if (model.getDownloadsFinishedForSession() > 0) {
                        System.out.println("Gracefully finished downloading all files.");
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(outputDir);
                        }
                    } else {
                        if (outputDir.exists() && (outputDir.listFiles() != null
                                && outputDir.listFiles().length == 0)) {
                            if (outputDir.delete()) {
                                System.out.println("Deleted empty output folder.");
                            }
                        }
                        System.out.println("Gracefully shutdown. No downloads have been made.");
                    }
                } else {
                    System.out.println("Failed to shutdown service. Forcing a shutdown. Data may be lost.");
                    service.shutdownNow();
                }
            } catch (Exception e) {
                System.out.println("Download service error: " + e.getLocalizedMessage());
            }
            model.stop();
        }).start();
    }

}
