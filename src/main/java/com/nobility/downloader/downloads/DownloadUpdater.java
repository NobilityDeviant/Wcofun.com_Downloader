package com.nobility.downloader.downloads;

import com.nobility.downloader.Model;
import com.nobility.downloader.utils.Tools;

public class DownloadUpdater implements Runnable {

    private final Model model;
    private final Download download;
    private double ratio = 0.0;
    private boolean running = true;

    public DownloadUpdater(final Model model, final Download download) {
        this.model = model;
        this.download = download;
    }

    @Override
    public void run() {
        while (running) {
            model.updateDownloadProgress(download, Tools.percentFormat.format(ratio));
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateRatio(double ratio) {
        this.ratio = ratio;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
