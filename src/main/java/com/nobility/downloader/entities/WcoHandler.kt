package com.nobility.downloader.entities

import com.nobility.downloader.Model
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.utils.Tools.downloadFile
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import java.io.File

class WcoHandler(private val databasePath: String, private val model: Model) {

    val wcoData: BoxStore = MyObjectBox.builder()
        .directory(File(databasePath + "wco"))
        .build()
    val seriesBox: Box<Series> = wcoData.boxFor(Series::class.java)
    val seriesImagesPath: String get() = databasePath + "SeriesImages" + File.separator

    suspend fun downloadSeriesImage(series: Series) {
        if (series.imageLink.isNullOrEmpty()) {
            return
        }
        val saveFolder = File(seriesImagesPath)
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            println("Unable to download series image: ${series.imageLink}. Save folder was unable to be created.")
            return
        }
        val saveFile = File(
            "${saveFolder.absolutePath}${File.separator}" +
                    Tools.titleForImages(series.name)
        )
        if (!saveFile.exists()) {
            try {
                downloadFile(
                    series.imageLink,
                    saveFile,
                    model.settings().integerSetting(Defaults.TIMEOUT) * 1000,
                    model.randomUserAgent
                )
                println("Successfully downloaded image: ${series.imageLink}")
            } catch (e: Exception) {
                println("Failed to download image for ${series.imageLink}. Error: ${e.localizedMessage}")
            }
        }
    }

    fun addOrUpdateSeries(series: Series): Boolean {
        if (series.id == 0L) {
            return replaceSeries(series)
        }
        return if (!seriesBox.contains(series.id)) {
            seriesBox.put(series)
            true
        } else {
            replaceSeries(series)
        }
    }

    private fun replaceSeries(series: Series): Boolean {
        try {
            seriesBox.query()
                .equal(Series_.name, series.name, StringOrder.CASE_SENSITIVE).build().use { query ->
                    val queried = query.find()
                    if (queried.isNotEmpty()) {
                        for (s in queried) {
                            if (s.matches(series)) {
                                return false
                            }
                        }
                        seriesBox.remove(queried)
                    }
                    seriesBox.put(series)
                    return true
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun seriesForSlug(slug: String): Series? {
        try {
            seriesBox.query()
                .equal(Series_.slug, slug, StringOrder.CASE_SENSITIVE)
                .build()
                .use { query -> return query.findUnique() }
        } catch (ignored: Exception) {
        }
        return null
    }
}