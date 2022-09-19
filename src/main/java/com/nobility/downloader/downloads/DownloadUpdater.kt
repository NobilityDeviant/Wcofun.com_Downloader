package com.nobility.downloader.downloads

import com.nobility.downloader.Model
import kotlinx.coroutines.delay

class DownloadUpdater(private val model: Model, private val download: Download) {

    private var running = true

    suspend fun run() {
        while (running) {
            model.updateDownloadProgress(download)
            delay(500)
        }
    }

    fun setRunning(running: Boolean) {
        this.running = running
    }
}