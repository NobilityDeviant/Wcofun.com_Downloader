package com.nobility.downloader.scraper

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.downloads.DownloadUpdater
import com.nobility.downloader.entities.Download
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools.fixTitle
import kotlinx.coroutines.*
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.*
import java.net.URL
import java.time.Duration
import javax.net.ssl.HttpsURLConnection

class VideoDownloader(model: Model) : DriverBase(model) {

    private var currentEpisode: Episode? = null
    private var currentDownload: Download? = null
    private val download: Download get() {
        return currentDownload!!
    }
    private val episode: Episode get() {
        return currentEpisode!!
    }
    private var retries = 0
    private val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun run() = withContext(Dispatchers.IO) {
        while (model.isRunning) {
            if (retries >= 10) {
                currentEpisode = null
                retries = 0
                continue
            }
            if (currentEpisode == null) {
                currentEpisode = model.nextLink
                if (currentEpisode == null) {
                    break
                }
                retries = 0
            }
            val link = episode.link
            if (link.isNullOrEmpty()) {
                println("Skipping episode (" + episode.name + ") with no link.")
                currentEpisode = null
                continue
            }
            model.incrementDownloadsInProgress()
            var series = model.settings().seriesForLink(episode.seriesLink)
            if (series == null) {
                series = model.settings().wcoHandler.seriesForLink(episode.seriesLink)
                if (series == null) {
                    println("Failed to find series for episode: ${episode.name}. Unable to create save folder.")
                }
            }
            val downloadFolderPath = model.settings().stringSetting(Defaults.SAVEFOLDER)
            var saveFolder = File(downloadFolderPath + File.separator
                    + if (series != null) fixTitle(series.name, true) else "")
            if (!saveFolder.exists()) {
                if (!saveFolder.mkdir()) {
                    println(
                        "Unable to create series save folder: ${saveFolder.absolutePath} " +
                                "Defaulting to $downloadFolderPath"
                    )
                    saveFolder = File(downloadFolderPath + File.separator)
                }
            }
            val saveFile = File(
                 saveFolder.absolutePath + File.separator
                        + fixTitle(episode.name, true) + ".mp4"
            )
            currentDownload = model.settings().downloadForLink(link)
            if (currentDownload != null) {
                if (download.downloadPath.isNullOrEmpty()
                    || !File(download.downloadPath).exists()
                    || download.downloadPath != saveFile.absolutePath) {
                    download.downloadPath = saveFile.absolutePath
                }
                model.addDownload(download)
                if (download.isComplete) {
                    println("[DB] Skipping completed video: " + episode.name)
                    download.downloading = false
                    download.queued = false
                    model.updateDownloadInDatabase(download, true)
                    currentEpisode = null
                    model.decrementDownloadsInProgress()
                    continue
                } else {
                    download.queued = true
                    model.updateDownloadFileSize(download)
                    model.updateDownloadProgress(download)
                }
            }
            driver.get(link)
            delay(2000)
            var flag = -1
            if (driver.pageSource.contains("anime-js-0")) {
                flag = 0
            } else if (driver.pageSource.contains("cizgi-js-0")) {
                flag = 1
            } else if (driver.pageSource.contains("video-js_html5_api")) {
                flag = 2
            }
            if (flag == -1) {
                println("Retrying... Failed to find video component for: $link")
                model.decrementDownloadsInProgress()
                continue
            }
            val wait = WebDriverWait(driver, Duration.ofSeconds(30))
            try {
                wait.pollingEvery(Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id(
                        when (flag) {
                            0 -> "anime-js-0"
                            1 -> "cizgi-js-0"
                            else -> "video-js_html5_api"
                        }
                    )))
            } catch (e: Exception) {
                println("Error waiting for $link to load. Retrying...")
                retries++
                model.decrementDownloadsInProgress()
                continue
            }
            val videoPlayer =
                driver.findElement(By.id(
                    when (flag) {
                        0 -> "anime-js-0"
                        1 -> "cizgi-js-0"
                        else -> "video-js_html5_api"
                    }
                ))
            if (videoPlayer == null || !videoPlayer.isDisplayed) {
                println("Failed to find the video player for $link. Retrying...")
                retries++
                model.decrementDownloadsInProgress()
                continue
            }
            val frameLink = videoPlayer.getAttribute("src")
            driver.get(frameLink)
            try {
                wait.pollingEvery(Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.className("vjs-big-play-button")))
            } catch (e: Exception) {
                println("Error waiting for frame video to load for $link. Retrying...")
                retries++
                model.decrementDownloadsInProgress()
                continue
            }
            val video = driver.findElement(By.className("vjs-big-play-button"))
            video.click()
            delay(3000)
            val src = driver.findElement(By.className("vjs-tech"))
            val videoLink = src.getAttribute("src")
            try {
                if (currentDownload == null) {
                    currentDownload = Download(
                        saveFile.absolutePath,
                        episode.name,
                        episode.link,
                        episode.seriesLink,
                        0L,
                        System.currentTimeMillis(),
                    )
                    download.queued = true
                    model.addDownload(download)
                }
                val originalFileSize = fileSize(URL(videoLink))
                if (originalFileSize <= 5000) {
                    println("Retrying... Error: Failed to determine file size for: " + currentEpisode!!.name)
                    retries++
                    model.decrementDownloadsInProgress()
                    continue
                }
                if (saveFile.exists()) {
                    if (saveFile.length() >= originalFileSize) {
                        println("[IO] Skipping completed video: " + episode.name)
                        download.downloadPath = saveFile.absolutePath
                        download.fileSize = originalFileSize
                        download.downloading = false
                        download.queued = false
                        model.updateDownloadInDatabase(download, true)
                        currentEpisode = null
                        model.decrementDownloadsInProgress()
                        continue
                    }
                } else {
                    saveFile.createNewFile()
                }
                println("Downloading: " + download.name)
                download.queued = false
                download.downloading = true
                download.fileSize = originalFileSize
                model.addDownload(download)
                model.updateDownloadInDatabase(download, true)
                downloadFile(URL(videoLink), saveFile)
                download.downloading = false
                //second time to ensure ui update
                model.updateDownloadInDatabase(download, true)
                if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                    model.incrementDownloadsFinished()
                    println("Successfully downloaded: " + download.name)
                    currentEpisode = null
                }
            } catch (e: IOException) {
                download.queued = true
                download.downloading = false
                model.updateDownloadInDatabase(download, true)
                //model.updateDownloadProgress(download)
                println(
                    """
    Unable to download ${download.name}
    Error: ${e.localizedMessage}
    Reattempting...
    """.trimIndent()
                )
                model.decrementDownloadsInProgress()
            }
        }
        killDriver()
        taskScope.cancel()
    }

    @Throws(IOException::class)
    private fun fileSize(url: URL): Long {
        val con = url.openConnection() as HttpsURLConnection
        con.requestMethod = "HEAD"
        con.useCaches = false
        con.addRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        con.addRequestProperty("Connection", "keep-alive")
        con.addRequestProperty("Sec-Fetch-Dest", "document")
        con.addRequestProperty("Sec-Fetch-Mode", "navigate")
        con.addRequestProperty("Sec-Fetch-Site", "cross-site")
        con.addRequestProperty("Sec-Fetch-User", "?1")
        con.addRequestProperty("Upgrade-Insecure-Requests", "1")
        con.addRequestProperty("User-Agent", userAgent)
        con.connectTimeout = model.settings().integerSetting(Defaults.TIMEOUT) * 1000
        con.readTimeout = model.settings().integerSetting(Defaults.TIMEOUT) * 1000
        return con.contentLength.toLong()
    }

    @Throws(IOException::class)
    private fun downloadFile(url: URL, output: File) {
        var offset = 0L
        if (output.exists()) {
            offset = output.length()
        }
        val con = url.openConnection() as HttpsURLConnection
        con.addRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        con.addRequestProperty("Connection", "keep-alive")
        con.addRequestProperty("Sec-Fetch-Dest", "document")
        con.addRequestProperty("Sec-Fetch-Mode", "navigate")
        con.addRequestProperty("Sec-Fetch-Site", "cross-site")
        con.addRequestProperty("Sec-Fetch-User", "?1")
        con.addRequestProperty("Upgrade-Insecure-Requests", "1")
        con.setRequestProperty("Range", "bytes=$offset-")
        con.connectTimeout = model.settings().integerSetting(Defaults.TIMEOUT) * 1000
        con.readTimeout = model.settings().integerSetting(Defaults.TIMEOUT) * 1000
        con.addRequestProperty("User-Agent", userAgent)
        //todo check for file space
        val completeFileSize = con.contentLength + offset //TODO might timeout, check for that
        if (offset != 0L) {
            println("Detected incomplete video: " + download.name + " - Attempting to finish it.")
        }
        val buffer = ByteArray(2048)
        val bis = BufferedInputStream(con.inputStream)
        val fos = FileOutputStream(output, true)
        val bos = BufferedOutputStream(fos, buffer.size)
        var count: Int
        var total = offset
        val updater = DownloadUpdater(model, download)
        taskScope.launch { updater.run() }
        while (bis.read(buffer, 0, 2048).also { count = it } != -1) {
            if (!model.isRunning) {
                println("Stopping video download at "
                        + total + "/" + completeFileSize + " - " + download.name)
                break
            }
            total += count.toLong()
            bos.write(buffer, 0, count)
        }
        updater.setRunning(false)
        bos.flush()
        bos.close()
        fos.flush()
        fos.close()
        bis.close()
        con.disconnect()
    }
}