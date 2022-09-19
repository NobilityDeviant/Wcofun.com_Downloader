package com.nobility.downloader.scraper

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.cache.Episode
import com.nobility.downloader.downloads.Download
import com.nobility.downloader.downloads.DownloadUpdater
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools.dateFormatted
import kotlinx.coroutines.*
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.*
import java.net.URL
import java.time.Duration
import javax.net.ssl.HttpsURLConnection


class VideoDownloader(model: Model, private val path: String) : DriverBase(model) {

    private var episode: Episode? = null
    private var currentDownload: Download? = null
    private var retries = 0
    private val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun run() = withContext(Dispatchers.IO) {
        while (model.isRunning) {
            if (model.settings().getInteger(Defaults.MAXEPISODES) != 0) {
                if (model.downloadsFinishedForSession >= model.settings().getInteger(Defaults.MAXEPISODES)) {
                    println(
                        "Finished downloading max links: " + model.settings()
                            .getInteger(Defaults.MAXEPISODES) + " Thread stopped."
                    )
                    break
                }
            }
            if (retries >= 10) {
                episode = null
                retries = 0
                continue
            }
            if (episode == null) {
                episode = model.nextLink
                if (episode == null) {
                    break
                }
            }
            val url = episode!!.link
            if (url.isNullOrEmpty()) {
                println("Skipping episode (" + episode!!.name + ") with no link.")
                episode = null
                continue
            }
            val save = File(
                path + File.separator
                        + episode!!.name + ".mp4"
            )
            currentDownload = model.getDownloadForUrl(url)
            if (currentDownload != null) {
                if (currentDownload!!.isComplete) {
                    println("Skipping completed video: " + episode!!.name)
                    currentDownload!!.downloadPath = save.absolutePath
                    currentDownload!!.isDownloading = false
                    currentDownload!!.isQueued = false
                    model.updateDownload(currentDownload!!)
                    episode = null
                    continue
                } else {
                    currentDownload!!.isQueued = true
                    currentDownload!!.updateProgress()
                }
            }
            driver.get(url)
            var flag = -1
            if (driver.pageSource.contains("anime-js-0")) {
                flag = 0
            } else if (driver.pageSource.contains("cizgi-js-0")) {
                flag = 1
            } else if (driver.pageSource.contains("video-js_html5_api")) {
                flag = 2
            }
            if (flag == -1) {
                println("Skipping... Failed to find video component for: $url")
                episode = null
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
                println("Error waiting for $url to load. Retrying...")
                retries++
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
                println("Failed to find the video player for $url. Retrying...")
                retries++
                continue
            }
            val frameLink = videoPlayer.getAttribute("src")
            driver.get(frameLink)
            try {
                wait.pollingEvery(Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.className("vjs-big-play-button")))
            } catch (e: Exception) {
                println("Error waiting for frame video to load for $url. Retrying...")
                retries++
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
                        save.absolutePath, dateFormatted, episode!!
                    )
                    currentDownload!!.isQueued = true
                    currentDownload!!.updateProgress()
                    model.addDownload(currentDownload!!)
                } /*else if (episode!!.name != currentDownload!!.name) {
                    currentDownload = Download(
                        save.absolutePath, dateFormatted, episode
                    )
                    currentDownload!!.isQueued = true
                    currentDownload!!.updateProgress()
                    model.addDownload(currentDownload!!)
                }*/
                val originalFileSize = fileSize(URL(videoLink))
                if (originalFileSize <= -1) {
                    println("Retrying... Error: Failed to determine file size for: " + episode!!.name)
                    retries++
                    continue
                }
                if (save.exists()) {
                    if (save.length() >= originalFileSize) {
                        println("Skipping completed video: " + episode!!.name)
                        currentDownload!!.downloadPath = save.absolutePath
                        currentDownload!!.fileSize = originalFileSize
                        currentDownload!!.isDownloading = false
                        currentDownload!!.isQueued = false
                        model.updateDownload(currentDownload!!)
                        model.tableView.refresh()
                        episode = null
                        continue
                    }
                } else {
                    save.createNewFile()
                }
                println("Downloading: " + episode!!.name)
                currentDownload!!.isQueued = false
                currentDownload!!.isDownloading = true
                currentDownload!!.fileSize = originalFileSize
                model.updateDownload(currentDownload!!)
                model.tableView.refresh()
                downloadFile(URL(videoLink), save)
                if (save.exists() && save.length() >= originalFileSize) {
                    model.incrementDownloadsFinished()
                    println("Successfully downloaded: " + episode!!.name)
                    episode = null
                }
            } catch (e: IOException) {
                currentDownload!!.isQueued = true
                currentDownload!!.isDownloading = false
                model.updateDownload(currentDownload!!)
                println(
                    """
    Unable to download $episode
    Error: ${e.localizedMessage}
    Reattempting...
    """.trimIndent()
                )
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
        con.connectTimeout = model.settings().getInteger(Defaults.TIMEOUT) * 1000
        con.readTimeout = model.settings().getInteger(Defaults.TIMEOUT) * 1000
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
        con.connectTimeout = model.settings().getInteger(Defaults.TIMEOUT) * 1000
        con.readTimeout = model.settings().getInteger(Defaults.TIMEOUT) * 1000
        con.addRequestProperty("User-Agent", userAgent)
        //todo check for file space
        val completeFileSize = con.contentLength + offset //TODO might timeout, check for that
        if (offset != 0L) {
            println("Detected incomplete video: " + episode!!.name + " - Attempting to finish it.")
        }
        val buffer = ByteArray(2048)
        val bis = BufferedInputStream(con.inputStream)
        val fos = FileOutputStream(output, true)
        val bos = BufferedOutputStream(fos, buffer.size)
        var count: Int
        var total = offset
        val updater = DownloadUpdater(model, currentDownload!!)
        taskScope.launch { updater.run() }
        while (bis.read(buffer, 0, 2048).also { count = it } != -1) {
            if (!model.isRunning) {
                println("Stopping video download at " + total + "/" + completeFileSize + " - " + episode!!.name)
                break
            }
            total += count.toLong()
            bos.write(buffer, 0, count)
        }
        updater.setRunning(false)
        currentDownload!!.isDownloading = false
        model.updateDownload(currentDownload!!)
        bos.flush()
        bos.close()
        fos.close()
        bis.close()
        con.disconnect()
    }
}