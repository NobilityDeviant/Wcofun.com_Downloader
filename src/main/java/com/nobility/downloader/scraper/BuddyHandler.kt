package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.entities.Series
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Resource
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.File

/**
 * Used to handle the start of the download and episode scraping process
 * @author Nobility
 */
class BuddyHandler(private val model: Model) {

    var url: String? = null
    private var series: Series? = null
    private var episode: Episode? = null
    private val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun update(url: String) {
        this.url = url
        println("Launching BuddyHandler to handle the link(s).")
        //all series links contains this key
        if (url.contains("/anime/")) {
            val cachedSeries = model.settings().wcoHandler.seriesForLink(url)
            if (cachedSeries != null) {
                series = cachedSeries
                //println("Found cache series. Using that data instead.")
                return
            } else {
                val downloadSeries = model.settings().seriesForLink(url)
                if (downloadSeries != null) {
                    series = downloadSeries
                    //println("Found recently downloaded series. Using that data instead.")
                    return
                }
            }
        }
        val scraper = LinkHandler(model)
        val result = scraper.handleLink(url)
        scraper.killDriver()
        if (result.data != null) {
            when (result.data) {
                is Series -> {
                    model.settings().addOrUpdateSeries(result.data)
                    model.settings().wcoHandler.addOrUpdateSeries(result.data)
                    series = result.data
                }
                is ToDownload -> {
                    if (result.data.series != null) {
                        series = result.data.series
                        episode = result.data.episode
                    } else if (result.data.episode != null) {
                        model.episodes.add(result.data.episode)
                        println("Downloading episode from: $url")
                    } else {
                        throw Exception("Failed to determine type for download.")
                    }
                }
                else -> {
                    throw Exception("Downloading failed. Don't lose hope. Please try again.")
                }
            }
        } else if (!result.message.isNullOrEmpty()) {
            throw Exception(result.message)
        }
    }

    fun launch() {
        if (series == null && episode == null) {
            println("Failed to find series or episode for this link.")
            kill()
            return
        }
        taskScope.launch task@{
            val saveDir = File(model.settings().stringSetting(Defaults.SAVEFOLDER))
            if (!saveDir.exists()) {
                if (!saveDir.mkdir()) {
                    withContext(Dispatchers.JavaFx) {
                        model.showError(
                            "Your download folder doesn't exist.",
                            "Be sure to set it inside the settings before downloading videos."
                        )
                        model.openSettings(0)
                    }
                    kill()
                    return@task
                }
            }
            if (series != null) {
                withContext(Dispatchers.JavaFx) {
                    model.openDownloadConfirm(series!!, episode)
                    kill()
                }
            } else {
                val downloader = VideoDownloader(model)
                try {
                    model.episodes.add(episode!!)
                    downloader.run()
                    if (model.downloadsFinishedForSession > 0) {
                        println("Gracefully finished downloading all files.")
                    } else {
                        println("Gracefully shutdown. No downloads have been made.")
                    }
                    kill()
                } catch (e: Exception) {
                    downloader.killDriver()
                    downloader.taskScope.cancel()
                    if (e.localizedMessage.contains("unknown error: cannot find")) {
                        println("Download service error. Unable to find your browser. Be sure to set it in the settings before downloading anything.")
                    } else {
                        println("Download service error: " + e.localizedMessage)
                    }
                    kill()
                }
            }
        }
    }

    suspend fun checkForNewEpisodes(
        series: Series
    ): Resource<NewEpisodes> = withContext(Dispatchers.IO) {
        println("Looking for new episodes for ${series.name}")
        val scraper = LinkHandler(model)
        val result = scraper.getSeriesEpisodes(series.link)
        scraper.killDriver()
        if (result.data != null) {
            if (result.data.size > series.episodes.size) {
                return@withContext Resource.Success(
                    NewEpisodes(compareForNewEpisodes(series, result.data), result.data)
                )
            }
        }
        return@withContext Resource.Error("No new episode have been found for ${series.name}.")
    }

    suspend fun updateSeriesDetails(
        _series: Series
    ): Resource<Series> = withContext(Dispatchers.IO) {
        val series = _series
        println("Updating series details for ${series.name}")
        val scraper = LinkHandler(model)
        val result = scraper.handleLink(series.link, true)
        scraper.killDriver()
        if (result.data is Series) {
            series.update(result.data)
            model.settings().wcoHandler.addOrUpdateSeries(series)
            return@withContext Resource.Success(result.data)
        } else {
            return@withContext Resource.Error("${result.message}")
        }
    }

    private fun compareForNewEpisodes(
        series: Series,
        latestEpisodes: List<Episode>
    ): List<Episode> {
        val episodes = ArrayList<Episode>()
        for (e in latestEpisodes) {
            if (!series.hasEpisode(e)) {
                episodes.add(e)
            }
        }
        return episodes
    }

    fun kill() {
        model.stop()
        taskScope.cancel()
    }
}