package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.cache.Episode
import com.nobility.downloader.cache.Series
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Resource
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*

/**
 * Used to handle the start of the download and episode scraping process
 * @author Nobility
 */
class BuddyHandler(private val model: Model) {

    var url: String? = null
    private val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun update(url: String) {
        this.url = url
        val scraper = LinkScraper(model)
        val result = scraper.handleLink(url)
        if (result.data != null) {
            when (result.data) {
                is Series -> {
                    val added = model.history().addSeries(result.data, true)
                    if (added) {
                        model.saveSeriesHistory()
                    }
                    model.episodes.addAll(result.data.episodes)
                    println("Downloading series from: $url \nFound Episodes: ${result.data.episodes.size}")
                }

                is Episode -> {
                    model.episodes.add(result.data)
                    println("Downloading episode from: $url")
                }

                else -> {
                    throw Exception("Downloading failed. Don't lose hope. Please try again.")
                }
            }
        } else if (!result.message.isNullOrEmpty()) {
            throw Exception(result.message)
        }
    }

    suspend fun checkForNewEpisodes(series: Series): Resource<List<Episode>> = withContext(Dispatchers.IO) {
        println("Looking for new episodes for ${series.name}")
        val scraper = LinkScraper(model)
        val result = scraper.getSeriesEpisodes(series.link)
        if (result.data != null) {
            if (result.data.size > series.episodes.size) {
                return@withContext Resource.Success(
                    compareForNewEpisodes(
                        series,
                        result.data
                    )
                )
            }
        }
        return@withContext Resource.Error("No new episode have been found for ${series.name}.")
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

    fun launch() {
        if (model.episodes.isEmpty()) {
            println("Failed to find any episodes for this link.")
            return
        }
        val saveFolder: String
        val name = model.episodes[0].name
        val nameToLowercase = name.lowercase(Locale.US)
        saveFolder = if (nameToLowercase.contains("episode")) {
            name.substring(0, nameToLowercase.indexOf("episode")).trim { it <= ' ' }
        } else {
            name
        }
        taskScope.launch task@{
            val saveDir = File(model.settings().getString(Defaults.SAVEFOLDER))
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
            val outputDir = File(saveDir.absolutePath + File.separator + saveFolder)
            if (!outputDir.exists()) {
                if (!outputDir.mkdir()) {
                    withContext(Dispatchers.JavaFx) {
                        model.showError(
                            "Unable to create series folder.",
                            saveDir.absolutePath + File.separator + saveFolder + " was unable to be created."
                        )
                    }
                    kill()
                    return@task
                }
            }
            if (model.settings().getBoolean(Defaults.SAVELINKS)) {
                try {
                    val w = BufferedWriter(
                        FileWriter(
                            outputDir.absolutePath
                                    + File.separator + "links.txt"
                        )
                    )
                    for (episode in model.episodes) {
                        w.write(episode.link)
                        w.newLine()
                    }
                    w.flush()
                    w.close()
                    println(
                        "Successfully saved the links to: " + outputDir.absolutePath
                                + File.separator + "links.txt"
                    )
                } catch (e: Exception) {
                    println("Failed to save links. Error: " + e.localizedMessage)
                }
            }
            try {
                var threads = model.settings().getInteger(Defaults.DOWNLOADTHREADS)
                if (model.episodes.size < threads) {
                    threads = model.episodes.size
                }
                val tasks = mutableListOf<Job>()
                for (i in 1..threads) {
                    tasks.add(
                        launch {
                            VideoDownloader(model, outputDir.absolutePath).run()
                        }
                    )
                }
                tasks.joinAll()
                if (model.downloadsFinishedForSession > 0) {
                    println("Gracefully finished downloading all files.")
                    model.openFolder(outputDir, false)
                } else {
                    if (outputDir.exists()) {
                        val files = outputDir.listFiles()
                        if (files != null && files.isEmpty()) {
                            if (outputDir.delete()) {
                                println("Deleted empty output folder.")
                            }
                        }
                    }
                    println("Gracefully shutdown. No downloads have been made.")
                }
            } catch (e: Exception) {
                println("Download service error: " + e.localizedMessage)
            }
            kill()
        }
    }

    fun kill() {
        model.stop()
        taskScope.cancel()
    }
}