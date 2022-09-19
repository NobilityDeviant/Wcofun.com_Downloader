package com.nobility.downloader.downloads;

import com.nobility.downloader.Model;

public class DownloadUpdater implements Runnable {

    private final Model model;
    private final Download download;
    private boolean running = true;

    public DownloadUpdater(final Model model, final Download download) {
        this.model = model;
        this.download = download;
    }

    @Override
    public void run() {
        while (running) {
            model.updateDownloadProgress(download);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
