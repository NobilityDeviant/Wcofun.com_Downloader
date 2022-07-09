package com.nobility.downloader.scraper;

import com.nobility.downloader.DriverBase;
import com.nobility.downloader.Model;
import com.nobility.downloader.downloads.Download;
import com.nobility.downloader.downloads.DownloadUpdater;
import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VideoDownloader extends DriverBase implements Runnable {

    private final String path;
    private Episode episode;
    private Download currentDownload;

    public VideoDownloader(Model model, String path) {
        super(model);
        this.path = path;
    }

    @Override
    public void run() {
        while (model.isRunning()) {
            if (model.settings().getInteger(Defaults.MAXLINKS) != 0) {
                if (model.getDownloadsFinishedForSession() >= model.settings().getInteger(Defaults.MAXLINKS)) {
                    System.out.println("Finished downloading max links: " + model.settings().getInteger(Defaults.MAXLINKS) + " Thread stopped.");
                    break;
                }
            }
            if (episode == null) {
                episode = model.getNextLink();
            }
            if (episode == null) {
                break;
            }
            String url = episode.getLink();
            if (StringChecker.isNullOrEmpty(url)) {
                System.out.println("Skipping episode (" + episode.getName() + ") with no link.");
                episode = null;
                continue;
            }
            File save = new File(path + File.separator
                    + episode.getName() + ".mp4");
            currentDownload = model.getDownloadForUrl(url);
            if (currentDownload != null) {
                if (currentDownload.isComplete()) {
                    System.out.println("Skipping completed video: " + episode.getName());
                    currentDownload.setDownloadPath(save.getAbsolutePath());
                    currentDownload.setDownloading(false);
                    currentDownload.setQueued(false);
                    model.updateDownload(currentDownload);
                    episode = null;
                    continue;
                } else {
                    currentDownload.setQueued(true);
                    currentDownload.updateProgress();
                }
            }
            driver.get(url);
            int flag = -1;
            if (driver.getPageSource().contains("anime-js-0")) {
                flag = 0;
            } else if (driver.getPageSource().contains("cizgi-js-0")) {
                flag = 1;
            } else if (driver.getPageSource().contains("video-js_html5_api")) {
                flag = 2;
            }
            if (flag == -1) {
                System.out.println("Skipping... Failed to find video component for: " + episode.getName());
                episode = null;
                continue;
            }
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            try {
                wait.pollingEvery(Duration.ofSeconds(3)).until(ExpectedConditions.visibilityOfElementLocated(By.id(flag == 0 ? "anime-js-0"
                        : flag == 1 ? "cizgi-js-0" : "video-js_html5_api")));
            } catch (Exception e) {
                System.out.println("Error waiting for video to load.");
                continue;
            }
            WebElement element1 = driver.findElement(By.id(flag == 0 ? "anime-js-0" : flag == 1 ? "cizgi-js-0" : "video-js_html5_api"));
            if (element1 == null || !element1.isDisplayed()) {
                System.out.println("Failed to load the video player.");
                continue;
            }
            String frameLink = element1.getAttribute("src");
            driver.get(frameLink);
            try {
                wait.pollingEvery(Duration.ofSeconds(3)).until(ExpectedConditions.visibilityOfElementLocated(By.className("vjs-big-play-button")));
            } catch (Exception e) {
                System.out.println("Error waiting for video to load.");
                continue;
            }
            WebElement video = driver.findElement(By.className("vjs-big-play-button"));
            video.click();
            sleep(3000);
            WebElement src = driver.findElement(By.className("vjs-tech"));
            String videoLink = src.getAttribute("src");
            try {
                if (currentDownload == null) {
                    currentDownload = new Download(save.getAbsolutePath(), episode.getName(),
                            Tools.getDateFormatted(), url);
                    currentDownload.setQueued(true);
                    currentDownload.updateProgress();
                    model.addDownload(currentDownload);
                } else if (!episode.getName().equals(currentDownload.getName())) {
                        currentDownload = new Download(save.getAbsolutePath(), episode.getName(),
                                Tools.getDateFormatted(), url);
                        currentDownload.setQueued(true);
                        currentDownload.updateProgress();
                        model.addDownload(currentDownload);
                }
                long originalFileSize = fileSize(new URL(videoLink));
                if (originalFileSize <= -1) {
                    System.out.println("Retrying... Error: Failed to determine file size for: " + episode.getName());
                    continue;
                }
                if (save.exists()) {
                    if (save.length() >= originalFileSize) {
                        System.out.println("Skipping completed video: " + episode.getName());
                        currentDownload.setDownloadPath(save.getAbsolutePath());
                        currentDownload.setFileSize(originalFileSize);
                        currentDownload.setDownloading(false);
                        currentDownload.setQueued(false);
                        model.updateDownload(currentDownload);
                        model.getTableView().refresh();
                        episode = null;
                        continue;
                    }
                } else {
                    save.createNewFile();
                }
                System.out.println("Downloading: " + episode.getName());
                currentDownload.setQueued(false);
                currentDownload.setDownloading(true);
                currentDownload.setSeriesLink(episode.getSeriesLink());
                currentDownload.setFileSize(originalFileSize);
                model.updateDownload(currentDownload);
                model.getTableView().refresh();
                downloadFile(new URL(videoLink), save);
                if (save.exists() && save.length() >= originalFileSize) {
                    model.incrementDownloadsFinished();
                    System.out.println("Successfully downloaded: " + episode.getName());
                    episode = null;
                }
            } catch (IOException e) {
                currentDownload.setQueued(true);
                currentDownload.setDownloading(false);
                model.updateDownload(currentDownload);
                System.out.println("Unable to download " + episode
                        + "\nError: " + e.getLocalizedMessage()
                        + "\nReattempting...");
            }
        }
        killDriver();
    }

    private long fileSize(URL url) throws IOException {
        HttpsURLConnection con = (HttpsURLConnection) (url.openConnection());
        con.setRequestMethod("HEAD");
        con.setUseCaches(false);
        con.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br");
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9");
        con.addRequestProperty("Connection", "keep-alive");
        con.addRequestProperty("Sec-Fetch-Dest", "document");
        con.addRequestProperty("Sec-Fetch-Mode", "navigate");
        con.addRequestProperty("Sec-Fetch-Site", "cross-site");
        con.addRequestProperty("Sec-Fetch-User", "?1");
        con.addRequestProperty("Upgrade-Insecure-Requests",  "1");
        con.addRequestProperty("User-Agent", userAgent);
        con.setConnectTimeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000);
        con.setReadTimeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000);
        return con.getContentLength();
    }

    private void downloadFile(URL url, File output) throws IOException {
        long offset = 0L;
        if (output.exists()) {
            offset = output.length();
        }
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br");
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9");
        con.addRequestProperty("Connection", "keep-alive");
        con.addRequestProperty("Sec-Fetch-Dest", "document");
        con.addRequestProperty("Sec-Fetch-Mode", "navigate");
        con.addRequestProperty("Sec-Fetch-Site", "cross-site");
        con.addRequestProperty("Sec-Fetch-User", "?1");
        con.addRequestProperty("Upgrade-Insecure-Requests",  "1");
        con.setRequestProperty("Range", "bytes=" + offset + "-");
        con.setConnectTimeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000);
        con.setReadTimeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000);
        con.addRequestProperty("User-Agent", userAgent);
        long completeFileSize = con.getContentLength() + offset; //TODO might timeout, check for that
        if (offset != 0) {
            System.out.println("Detected incomplete video: " + episode.getName() + " - Attempting to finish it.");
        }
        byte[] buffer = new byte[2048];
        BufferedInputStream bis = new BufferedInputStream(con.getInputStream());
        FileOutputStream fos = new FileOutputStream(output, true);
        BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length);
        int count;
        long total = offset;
        final DownloadUpdater updater = new DownloadUpdater(model, currentDownload);
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(updater);
        while ((count = bis.read(buffer, 0, 2048)) != -1) {
            if (!model.isRunning()) {
                System.out.println("Stopping video download at " + total + "/" + completeFileSize + " - " + episode.getName());
                break;
            }
            total += count;
            bos.write(buffer, 0, count);
        }
        updater.setRunning(false);
        service.shutdown();
        try {
            if (!service.awaitTermination(15, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException ignored) {}
        currentDownload.setDownloading(false);
        model.updateDownload(currentDownload);
        bos.flush();
        bos.close();
        fos.close();
        bis.close();
        con.disconnect();
    }

    @SuppressWarnings("all")
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {}
    }
}
