package com.nobility.downloader.scraper;

import com.nobility.downloader.DriverBase;
import com.nobility.downloader.Model;
import com.nobility.downloader.downloads.Download;
import com.nobility.downloader.downloads.DownloadUpdater;
import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.Tools;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("unused")
public class VideoDownloader extends DriverBase implements Runnable {

    private final String path;
    private final String userAgent;
    private Episode episode;
    private Download currentDownload;

    public VideoDownloader(Model model, String path) {
        super(model);
        this.path = path;
        userAgent = model.getRandomUserAgent();
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
            File save = new File(path + File.separator + episode.getName() + ".mp4");
            currentDownload = model.getDownloadForUrl(episode.getLink());
            if (currentDownload != null && currentDownload.isComplete()) {
                System.out.println("Skipping completed video: " + episode.getName());
                currentDownload.setDownloadPath(save.getAbsolutePath());
                currentDownload.setProgressProperty("100%");
                currentDownload.setDownloading(false);
                model.updateDownload(currentDownload);
                episode = null;
                continue;
            }
            String url = episode.getLink();
            if (url == null || url.isEmpty()) {
                System.out.println("Skipping episode (" + episode.getName() + ") with no link.");
                episode = null;
                continue;
            }
            driver.get(url);
            int flag = -1;
            if (driver.getPageSource().contains("anime-js-0")) {
                flag = 0;
            } else if (driver.getPageSource().contains("cizgi-js-0")) {
                flag = 1;
            }
            if (flag == -1) {
                episode = null;
                continue;
            }
            if (driver.getPageSource().contains("anime-js-0")) {
                flag = 0;
            } else if (driver.getPageSource().contains("cizgi-js-0")) {
                flag = 1;
            } else if (driver.getPageSource().contains("video-js_html5_api")) {
                flag = 2;
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
                            Tools.getDateFormatted(), url, "0%");
                    model.addDownload(currentDownload);
                } else {
                    if (!episode.getName().equals(currentDownload.getName())) {
                        currentDownload = new Download(save.getAbsolutePath(), episode.getName(),
                                Tools.getDateFormatted(), url, "0%");
                        model.addDownload(currentDownload);
                    }
                }
                long originalFileSize = fileSize(new URL(videoLink));
                if (save.exists() && save.length() >= originalFileSize) {
                    System.out.println("Skipping completed video: " + episode.getName());
                    currentDownload.setDownloadPath(save.getAbsolutePath());
                    currentDownload.setProgressProperty("100%");
                    currentDownload.setFileSize(originalFileSize);
                    currentDownload.setDownloading(false);
                    model.updateDownload(currentDownload);
                    model.getTableView().refresh();
                    episode = null;
                    continue;
                }
                System.out.println("Downloading: " + episode.getName());
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
                System.out.println("Unable to download " + episode
                        + "\nError: " + e.getLocalizedMessage()
                        + "\nReattempting...");
            }
        }
        killDriver();
    }

    private long fileSize(URL url) throws IOException {
        HttpsURLConnection con = (HttpsURLConnection) (url.openConnection());
        con.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br");
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9");
        con.addRequestProperty("Connection", "keep-alive");
        con.addRequestProperty("Sec-Fetch-Dest", "document");
        con.addRequestProperty("Sec-Fetch-Mode", "navigate");
        con.addRequestProperty("Sec-Fetch-Site", "cross-site");
        con.addRequestProperty("Sec-Fetch-User", "?1");
        con.addRequestProperty("Upgrade-Insecure-Requests",  "1");
        driver.manage().getCookies().forEach(cookie -> con.addRequestProperty("Cookie", cookie.toString()));
        con.addRequestProperty("User-Agent", userAgent);
        con.setConnectTimeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000);
        con.setReadTimeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000);
        return con.getContentLength();
    }

    private void downloadFile(URL url, File output) throws IOException {
        long offset = 0L;
        if (!output.exists()) {
            if (!output.createNewFile()) {
                System.out.println("Unable to create file for: " + currentDownload.getName());
            }
        } else {
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
        driver.manage().getCookies().forEach(cookie -> con.addRequestProperty("Cookie", cookie.toString()));
        con.addRequestProperty("User-Agent", userAgent);
        long completeFileSize = con.getContentLength() + offset; //TODO might timeout, check for that
        if (offset != 0) {
            System.out.println("Detected incomplete video: " + episode.getName() + " Attempting to finish it.");
        }
        InputStream in = con.getInputStream();
        FileOutputStream fos = new FileOutputStream(output, true);
        byte[] buffer = new byte[1024];
        int count;
        long total = offset;
        final DownloadUpdater updater = new DownloadUpdater(model, currentDownload);
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(updater);
        while ((count = in.read(buffer)) != -1) {
            if (!model.isRunning()) {
                System.out.println("Stopping video download at " + total + "/" + completeFileSize + " - " + episode.getName());
                break;
            }
            total += count;
            updater.updateRatio(total / (double) completeFileSize);
            fos.write(buffer, 0, count);
        }
        model.updateDownloadProgress(currentDownload, Tools.percentFormat.format(total / (double) completeFileSize));
        updater.setRunning(false);
        service.shutdown();
        currentDownload.setDownloading(false);
        model.updateDownload(currentDownload);
        fos.close();
        con.disconnect();
        in.close();
    }

    public static int readInputStreamWithTimeout(InputStream is, byte[] b, int timeoutMillis) throws IOException  {
        int bufferOffset = 0;
        long maxTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < b.length) {
            int readLength = Math.min(is.available(), b.length - bufferOffset);
            // can alternatively use bufferedReader, guarded by isReady():
            int readResult = is.read(b, bufferOffset, readLength);
            if (readResult == -1) {
                break;
            }
            bufferOffset += readResult;
        }
        return bufferOffset;
    }

    @SuppressWarnings("all")
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {}
    }
}
