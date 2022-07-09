package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.AlertBox
import javafx.scene.control.Alert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Used to handle the start of the download and episode scraping process
 * @author Nobility
 */
class BuddyHandler(private val model: Model) {

    var url: String? = null
    private val taskScope = CoroutineScope(Dispatchers.Default)
    private val uiScope = CoroutineScope(Dispatchers.JavaFx)

    @Throws(Exception::class)
    fun update(url: String, chrome: Boolean) {
        this.url = url
        val service = Executors.newSingleThreadExecutor()
        service.submit(if (chrome) ChromeDriverLinkScraper(model, url) else JSoupLinkScraper(model, url))
        service.shutdown()
        if (service.awaitTermination(2, TimeUnit.MINUTES)) {
            if (model.links.isNotEmpty()) {
                println("Launching downloader for " + url + " Episodes: " + model.links.size)
            } else {
                if (!chrome) {
                    throw Exception("Failed to use Jsoup. Using Chrome Driver instead.")
                }
            }
        }
    }

    fun launch() {
        model.start()
        val saveFolder: String
        val name = model.links[0].name
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
                    uiScope.launch {
                        AlertBox.show(
                            Alert.AlertType.ERROR, "Your download folder doesn't exist.", "Be sure to set it inside " +
                                    "the settings before downloading videos."
                        )
                    }
                    model.stop()
                    uiScope.cancel()
                    cancel()
                    return@task
                }
            }
            val outputDir = File(saveDir.absolutePath + File.separator + saveFolder)
            if (!outputDir.exists()) {
                if (!outputDir.mkdir()) {
                    uiScope.launch {
                        AlertBox.show(
                            Alert.AlertType.ERROR, "Unable to create series folder.",
                            saveDir.absolutePath + File.separator + saveFolder + " was unable to be created."
                        )
                    }
                    model.stop()
                    uiScope.cancel()
                    cancel()
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
                    for (episode in model.links) {
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
                var threads = model.settings().getInteger(Defaults.THREADS)
                if (model.links.size < threads) {
                    threads = model.links.size
                }
                val service = Executors.newFixedThreadPool(threads)
                var i = 0
                while (i < threads) {
                    service.submit(VideoDownloader(model, outputDir.absolutePath))
                    i++
                }
                service.shutdown()
                if (service.awaitTermination(8, TimeUnit.HOURS)) {
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
                } else {
                    println("Failed to shutdown service. Forcing a shutdown. Data may be lost.")
                    service.shutdownNow()
                }
            } catch (e: Exception) {
                println("Download service error: " + e.localizedMessage)
            }
            model.stop()
            uiScope.cancel()
            cancel()
        }
    }
}