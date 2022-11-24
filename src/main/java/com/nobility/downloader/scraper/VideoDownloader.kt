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
    private val download: Download get() = currentDownload!!
    private val episode: Episode get() = currentEpisode!!
    private var retries = 0
    val taskScope = CoroutineScope(Dispatchers.Default)

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
                println("Skipping episode (${episode.name}) with no link.")
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
            var saveFolder = File(
                downloadFolderPath + File.separator
                        + if (series != null) fixTitle(series.name, true) else ""
            )
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
                    || download.downloadPath != saveFile.absolutePath
                ) {
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
            val wait = WebDriverWait(driver, Duration.ofSeconds(30))
            val animeJs = "anime-js-0"
            val cizgiJs = "cizgi-js-0"
            val videoJs = "video-js_html5_api"
            var foundVideoFrame = false
            var flag = 0
            while (flag < 2) {
                try {
                    wait.pollingEvery(Duration.ofSeconds(2))
                        .withTimeout(Duration.ofSeconds(6))
                        .until(
                            ExpectedConditions.visibilityOfElementLocated(
                                By.id(
                                    when (flag) {
                                        0 -> animeJs
                                        1 -> cizgiJs
                                        else -> ""
                                    }
                                )
                            )
                        )
                    val frame = driver.findElement(By.id(
                        when (flag) {
                            0 -> animeJs
                            1 -> cizgiJs
                            else -> ""
                        }
                    ))
                    //frame.click()
                    driver.switchTo().frame(frame)
                    foundVideoFrame = true
                    break
                } catch (e: Exception) {
                    flag++
                }
            }
            if (!foundVideoFrame) {
                println("Failed to find video frame for $link. Retrying...")
                retries++
                model.decrementDownloadsInProgress()
                continue
            }
            var videoLink: String
            try {
                val videoPlayer = driver.findElement(By.id(videoJs))
                if (videoPlayer == null || !videoPlayer.isDisplayed) {
                    println("Failed to find the video player for $link. Retrying...")
                    retries++
                    model.decrementDownloadsInProgress()
                    continue
                }
                videoLink = videoPlayer.getAttribute("src")
            } catch (e: Exception) {
                println("Failed to find video player inside frame for $link. Retrying...")
                retries++
                model.decrementDownloadsInProgress()
                continue
            }
            if (videoLink.isEmpty()) {
                println("Failed to find video link for $link. Retrying...")
                retries++
                model.decrementDownloadsInProgress()
                continue
            }
            //println(videoLink)
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
                    println("Retrying... Failed to determine file size for: " + currentEpisode!!.name)
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
                downloadVideo(URL(videoLink), saveFile)
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
                println(
                    "Unable to download ${download.name}" +
                            "\nError: ${e.localizedMessage}" +
                            "\nReattempting..."
                )
                model.decrementDownloadsInProgress()
            }
        }
        killDriver()
        taskScope.cancel()
    }

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

    private fun downloadVideo(url: URL, output: File) {
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
        val completeFileSize = con.contentLength + offset
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
                println("Stopping video download at $total / $completeFileSize - ${download.name}")
                break
            }
            total += count.toLong()
            bos.write(buffer, 0, count)
        }
        updater.running = false
        bos.flush()
        bos.close()
        fos.flush()
        fos.close()
        bis.close()
        con.disconnect()
    }
}