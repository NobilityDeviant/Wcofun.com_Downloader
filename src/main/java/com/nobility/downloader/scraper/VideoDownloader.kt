package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.driver.DriverBase
import com.nobility.downloader.entities.Download
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.settings.Quality
import com.nobility.downloader.utils.DownloadUpdater
import com.nobility.downloader.utils.JavascriptHelper
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools.fixTitle
import kotlinx.coroutines.*
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.*
import java.net.URL
import java.time.Duration
import javax.net.ssl.HttpsURLConnection


/**
 * This is the class that handles the scraping of the video link.
 */
class VideoDownloader(model: Model) : DriverBase(model) {

    private var currentEpisode: Episode? = null
    private var currentDownload: Download? = null
    private val download: Download get() = currentDownload!!
    private val episode: Episode get() = currentEpisode!!
    private var retries = 0
    private var resRetries = 0

    //private var failedToExtractResolutionLink = false
    //private var failedToVisitFrame = false
    val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun run() = withContext(Dispatchers.IO) {
        while (model.isRunning) {
            if (retries >= 15) {
                if (currentEpisode != null) {
                    println("Reached max retries (15) for ${episode.name}. Skipping episode...")
                }
                currentEpisode = null
                resRetries = 0
                retries = 0
                continue
            }
            if (currentEpisode == null) {
                currentEpisode = model.nextEpisode
                if (currentEpisode == null) {
                    break
                }
                resRetries = 0
                retries = 0
            }
            val slug = episode.slug
            if (slug.isNullOrEmpty()) {
                println("Skipping episode (${episode.name}) with no slug.")
                currentEpisode = null
                continue
            }
            model.incrementDownloadsInProgress()
            var downloadLink = ""
            var qualityOption = Quality.qualityForTag(
                model.settings().stringSetting(Defaults.QUALITY)
            )
            if (resRetries < 3) {
                val result = detectAvailableResolutions(slug)
                if (result.errorCode != -1) {
                    val errorCode = ErrorCode.errorCodeForCode(result.errorCode)
                    if (errorCode == ErrorCode.NO_FRAME) {
                        resRetries++
                        model.decrementDownloadsInProgress()
                        model.debugErr("Failed to find frame for resolution check. Retrying...")
                        continue
                    } else if (errorCode == ErrorCode.IFRAME_FORBIDDEN) {
                        resRetries = 3
                        model.debugErr(
                            "Failed to find video frame for: $slug" +
                                    "\nPlease report this in github issues with the browser used and the video you are trying to download."
                        )
                        continue
                    } else if (errorCode == ErrorCode.FAILED_EXTRACT_RES) {
                        resRetries = 3
                        model.debugErr("Failed to extract resolution links.")
                    } else if (errorCode == ErrorCode.NO_JS) {
                        resRetries = 3
                        println(
                            "This browser doesn't support JavascriptExecutor."
                        )
                    }
                }
                if (result.data != null) {
                    qualityOption = Quality.bestQuality(
                        qualityOption,
                        result.data.map { it.quality }
                    )
                    result.data.forEach {
                        if (it.quality == qualityOption) {
                            downloadLink = it.downloadLink
                        }
                    }
                } else {
                    model.debugNote("Failed to find resolution download links, defaulting to ${Quality.LOW.tag} quality.")
                    qualityOption = Quality.LOW
                }
            }
            var series = model.settings().seriesForSlug(episode.seriesSlug)
            if (series == null) {
                series = model.settings().wcoHandler.seriesForSlug(episode.seriesSlug)
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
            val extraQualityName = if (qualityOption != Quality.LOW) " (${qualityOption.tag})" else ""
            val episodeName = fixTitle(episode.name, true) + extraQualityName
            val saveFile = File(
                saveFolder.absolutePath + File.separator
                        + "$episodeName.mp4"
            )
            currentDownload = model.settings().downloadForSlugAndQuality(slug, qualityOption)
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
            if (downloadLink.isEmpty()) {
                driver.get(model.linkForSlug(slug))
                val wait = WebDriverWait(driver, Duration.ofSeconds(30))
                val animeJs = "anime-js-0"
                val cizgiJs = "cizgi-js-0"
                val videoJs = "video-js_html5_api"
                var foundVideoFrame = false
                //val checkForQuality = qualityOption != Quality.LOW
                //var skip = false
                var flag = 0
                while (flag < 2) {
                    try {
                        wait.pollingEvery(Duration.ofSeconds(1))
                            .withTimeout(Duration.ofSeconds(10))
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
                        val frame = driver.findElement(
                            By.id(
                                when (flag) {
                                    0 -> animeJs
                                    1 -> cizgiJs
                                    else -> ""
                                }
                            )
                        )
                        driver.switchTo().frame(frame)
                        foundVideoFrame = true
                        /*if (!failedToVisitFrame) {
                            val frameLink = frame.getAttribute("src")
                            if (!frameLink.isNullOrEmpty()) {
                                //must redirect like this or else we get forbidden
                                js.executeScript(JavascriptHelper.changeUrl(frameLink))
                                if (driver.pageSource.contains("403 Forbidden")) {
                                    println(
                                        "Failed to video frame for: $slug" +
                                                "\nPlease report this in github issues." +
                                                "\nRetrying with original method..."
                                    )
                                    skip = true
                                    failedToVisitFrame = true
                                    break
                                } else {
                                    foundVideoFrame = true
                                }
                            }
                        } else {
                           driver.switchTo().frame(frame)
                            foundVideoFrame = true
                        }*/
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                        model.debugErr("Failed to find flag $flag. Trying next one.", e)
                        flag++
                    }
                }
                //if (skip) {
                //  model.decrementDownloadsInProgress()
                //continue
                //}
                if (!foundVideoFrame) {
                    model.debugErr("No flag was found. IFrame not found in webpage.")
                    model.debugWriteErrorToFile("Flag Not Found:\n" + driver.pageSource, "source")
                    println("Failed to find video frame for ${model.linkForSlug(slug)}. Retrying...")
                    retries++
                    model.decrementDownloadsInProgress()
                    continue
                }
                var videoLinkError: String
                try {
                    val videoPlayer = driver.findElement(By.id(videoJs))
                    //this makes it wait so it doesn't throw an error everytime
                    wait.pollingEvery(Duration.ofSeconds(1))
                        .withTimeout(Duration.ofSeconds(15))
                        .until(ExpectedConditions.attributeToBeNotEmpty(videoPlayer, "src"))
                    downloadLink = videoPlayer.getAttribute("src")
                    videoLinkError = videoPlayer.getAttribute("innerHTML")
                } catch (e: Exception) {
                    model.debugErr("Found frame, but failed to find $videoJs", e)
                    println("Failed to find video player inside frame for ${model.linkForSlug(slug)} Retrying...")
                    retries++
                    model.decrementDownloadsInProgress()
                    continue
                }
                if (downloadLink.isEmpty()) {
                    model.debugErr("Found $videoJs, but the video link was empty? No javascript found?")
                    if (videoLinkError.isNotEmpty()) {
                        model.debugErr("Empty link source: \n${videoLinkError.trim()}")
                    }
                    println("Failed to find video link for ${model.linkForSlug(slug)}. Retrying...")
                    retries++
                    model.decrementDownloadsInProgress()
                    continue
                }
            }
            model.debugNote("Successfully found video link with $retries retries.")
            try {
                if (currentDownload == null) {
                    currentDownload = Download(
                        saveFile.absolutePath,
                        episode.name,
                        episode.slug,
                        episode.seriesSlug,
                        qualityOption.resolution,
                        0L,
                        System.currentTimeMillis(),
                    )
                    download.queued = true
                    model.addDownload(download)
                    model.debugNote("Created new download for ${episode.name}")
                } else {
                    model.debugNote("Using existing download for ${episode.name}")
                }
                val originalFileSize = fileSize(URL(downloadLink))
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
                    try {
                        val created = saveFile.createNewFile()
                        if (!created) {
                            throw Exception("No error thrown.")
                        }
                    } catch (e: Exception) {
                        model.debugErr("Unable to create video file for ${episode.name}", e)
                        println("Failed to create new video file for ${episode.name} Retrying...")
                        retries++
                        model.decrementDownloadsInProgress()
                        continue
                    }
                }
                println("[${qualityOption.tag}] Downloading: " + download.name)
                download.queued = false
                download.downloading = true
                download.fileSize = originalFileSize
                model.addDownload(download)
                model.updateDownloadInDatabase(download, true)
                downloadVideo(URL(downloadLink), saveFile)
                download.downloading = false
                //second time to ensure ui update
                model.updateDownloadInDatabase(download, true)
                if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                    model.incrementDownloadsFinished()
                    println("Successfully downloaded: $episodeName")
                    model.debugNote("Successfully downloaded: $episodeName")
                    currentEpisode = null
                }
            } catch (e: IOException) {
                download.queued = true
                download.downloading = false
                model.updateDownloadInDatabase(download, true)
                println(
                    "Unable to download $episodeName" +
                            "\nError: ${e.localizedMessage}" +
                            "\nReattempting..."
                )
                model.debugErr("Failed to download $episodeName", e)
                model.decrementDownloadsInProgress()
            }
        }
        killDriver()
        taskScope.cancel()
    }

    private data class QualityAndDownload(val quality: Quality, val downloadLink: String)

    private enum class ErrorCode(val code: Int) {
        NO_FRAME(0),
        IFRAME_FORBIDDEN(1),
        FAILED_EXTRACT_RES(2),
        NO_JS(3);

        companion object {
            fun errorCodeForCode(code: Int?): ErrorCode? {
                if (code == null) {
                    return null
                }
                values().forEach {
                    if (code == it.code) {
                        return it
                    }
                }
                return null
            }
        }
    }

    private suspend fun detectAvailableResolutions(
        slug: String
    ): Resource<List<QualityAndDownload>> {
        if (driver !is JavascriptExecutor) {
            return Resource.ErrorCode(ErrorCode.NO_JS.code)
        }
        val fullLink = model.linkForSlug(slug)
        println("Scraping resolution links from $fullLink")
        val qualities = mutableListOf<QualityAndDownload>()
        val js = driver as JavascriptExecutor
        driver.get(fullLink)
        val wait = WebDriverWait(driver, Duration.ofSeconds(30))
        val animeJs = "anime-js-0"
        val cizgiJs = "cizgi-js-0"
        var foundVideoFrame = false
        var flag = 0
        while (flag < 2) {
            try {
                model.debugNote("Trying to find frame for resolution with flag: $flag")
                wait.pollingEvery(Duration.ofSeconds(1))
                    .withTimeout(Duration.ofSeconds(10))
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
                val frame = driver.findElement(
                    By.id(
                        when (flag) {
                            0 -> animeJs
                            1 -> cizgiJs
                            else -> ""
                        }
                    )
                )
                val frameLink = frame.getAttribute("src")
                model.debugNote("Found frame for resolution with flag: $flag and link: $frameLink")
                if (!frameLink.isNullOrEmpty()) {
                    model.debugNote("Executing resolution javascript function.")
                    //must redirect like this or else we get forbidden (
                    js.executeScript(JavascriptHelper.advancedChangeUrl(frameLink))
                    model.debugNote("Javascript executed. Waiting 5 seconds for page change.")
                    delay(5000)
                    if (driver.pageSource.contains("403 Forbidden")) {
                        return Resource.ErrorCode(ErrorCode.IFRAME_FORBIDDEN.code)
                    } else {
                        foundVideoFrame = true
                    }
                }
                break
            } catch (e: Exception) {
                e.printStackTrace()
                model.debugErr("Failed to find flag $flag for $slug. Trying next one.", e)
                flag++
                continue
            }
        }
        if (!foundVideoFrame) {
            return Resource.ErrorCode(ErrorCode.NO_FRAME.code)
        }
        //vp.ready(function() { indicates that the video has multiple resolutions (iframe only)
        //val hasOptions = driver.pageSource.contains("vp.ready(function() {")
        //this is better though
        val has720 = driver.pageSource.contains("obj720")
        val has1080 = driver.pageSource.contains("obj1080")
        for (quality in Quality.qualityList(has720, has1080)) {
            try {
                val src = driver.pageSource
                val linkKey1 = "  });    \n" +
                        "      });\n" +
                        "      \n" +
                        "      \$.getJSON(\""
                val linkKey2 = "\", function(response){"
                val linkIndex1 = src.indexOf(linkKey1)
                val linkIndex2 = src.indexOf(linkKey2)
                val functionLink = driver.pageSource.substring(
                    linkIndex1 + linkKey1.length, linkIndex2
                )
                js.executeScript(
                    JavascriptHelper.changeUrlToVideoFunction(
                        functionLink,
                        quality
                    )
                )
                delay(5000)
                if (driver.pageSource.contains("404 Not Found")) {
                    model.debugErr("Failed to find $quality quality link for $slug")
                    continue
                }
                val videoLink = driver.currentUrl
                if (videoLink.isNotEmpty()) {
                    qualities.add(
                        QualityAndDownload(quality, videoLink)
                    )
                    model.debugNote(
                        "Found $quality link for $slug"
                    )
                }
                driver.navigate().back()
                delay(2000)
            } catch (e: Exception) {
                continue
            }
        }
        return if (qualities.isEmpty()) {
            Resource.ErrorCode(ErrorCode.FAILED_EXTRACT_RES.code)
        } else {
            Resource.Success(qualities)
        }
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