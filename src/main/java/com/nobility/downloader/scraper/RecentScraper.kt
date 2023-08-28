package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.driver.DriverBase
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.utils.Tools.titleForImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

class RecentScraper(model: Model): DriverBase(model) {

    suspend fun run(): Resource<RecentResult> = withContext(Dispatchers.IO) {
        val data = ArrayList<RecentResult.Data>()
        try {
            driver.get(model.wcoUrl)
            val doc = Jsoup.parse(driver.pageSource)
            val recentEpisodeHolder = doc.getElementById("sidebar_right")
            if (recentEpisodeHolder != null) {
                val ul = recentEpisodeHolder.select("ul")
                for (uls in ul) {
                    val lis = uls.select("li")
                    for (li in lis) {
                        val img = li.select("div.img")
                        var seriesImage = img.select("a").select("img").attr("src")
                        if (!seriesImage.startsWith("https:")) {
                            seriesImage = "https:$seriesImage"
                        }
                        val episode = li.select("div.recent-release-episodes").select("a")
                        val episodeName = episode.text()
                        val episodeLink = episode.attr("href")
                        val seriesImagesPath = model.settings().wcoHandler.seriesImagesPath
                        val seriesImagesFolder = File(seriesImagesPath)
                        if (!seriesImagesFolder.exists()) {
                            seriesImagesFolder.mkdirs()
                        }
                        val imagePath = seriesImagesPath + titleForImages(episodeName)
                        val imageFile = File(imagePath)
                        if (!imageFile.exists()) {
                            Tools.downloadFile(
                                seriesImage,
                                imageFile,
                                model.settings().integerSetting(Defaults.TIMEOUT) * 1000,
                                model.randomUserAgent
                            )
                        }
                        /*println(
                            "Found recent episode!" +
                                    "\nSeries Image: $seriesImage" +
                                    "\nEpisode Name: $episodeName" +
                                    "\nEpisode Link: $episodeLink"
                        )*/
                        data.add(RecentResult.Data(imagePath, episodeName, episodeLink, false))
                    }
                }
            }
            val recentSeriesHolder = doc.getElementById("sidebar_right2")
            if (recentSeriesHolder != null) {
                val ul = recentSeriesHolder.select("ul")
                for (uls in ul) {
                    val lis = uls.select("li")
                    for (li in lis) {
                        val img = li.select("div.img")
                        var seriesImage = img.select("a").select("img").attr("src")
                        if (!seriesImage.startsWith("https:")) {
                            seriesImage = "https:$seriesImage"
                        }
                        val series = li.select("div.recent-release-episodes").select("a")
                        val seriesName = series.text()
                        val seriesLink = series.attr("href")
                        val seriesImagesPath = model.settings().wcoHandler.seriesImagesPath
                        val seriesImagesFolder = File(seriesImagesPath)
                        if (!seriesImagesFolder.exists()) {
                            seriesImagesFolder.mkdirs()
                        }
                        val imagePath = seriesImagesPath + titleForImages(seriesName)
                        val imageFile = File(imagePath)
                        if (!imageFile.exists()) {
                            Tools.downloadFile(
                                seriesImage,
                                imageFile,
                                model.settings().integerSetting(Defaults.TIMEOUT) * 1000,
                                model.randomUserAgent
                            )
                        }
                        /*println(
                            "Found recent series!" +
                                    "\nSeries Image: $seriesImage" +
                                    "\nSeries Name: $seriesName" +
                                    "\nSeries Link: $seriesLink"
                        )*/
                        data.add(RecentResult.Data(imagePath, seriesName, seriesLink, true))
                    }
                }
            }
        } catch (e: Exception) {
            killDriver()
            return@withContext Resource.Error(e)
        }
        killDriver()
        return@withContext Resource.Success(RecentResult(data))
    }

}