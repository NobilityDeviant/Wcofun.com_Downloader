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

    private val wcoData: BoxStore = MyObjectBox.builder()
        .directory(File(databasePath + "wco"))
        .build()
    val seriesBox: Box<Series> = wcoData.boxFor(Series::class.java)
    private val linksBox: Box<Links> = wcoData.boxFor(Links::class.java)
    val seriesImagesPath: String get() = databasePath + "SeriesImages" + File.separator

    private val mainLinks: Links
        get() {
            if (linksBox.isEmpty) {
                linksBox.put(Links(emptyList(), emptyList(), emptyList(), emptyList()))
            }
            return linksBox.all[0]
        }

    fun areLinksEmpty(): Boolean {
        return getSubbedLinks().isEmpty() || getDubbedLinks().isEmpty()
                || getCartoonsLinks().isEmpty() || getMoviesLinks().isEmpty()
    }

    fun setDubbedLinks(dubbed: List<String>) {
        val links = mainLinks
        links.dubbed.clear()
        links.dubbed.addAll(dubbed)
        linksBox.put(links)
    }

    private fun getDubbedLinks(): List<String> {
        val links = mainLinks
        return if (links.dubbed != null) {
            links.dubbed
        } else {
            emptyList()
        }
    }

    fun setSubbedLinks(subbed: List<String>) {
        val links = mainLinks
        links.subbed.clear()
        links.subbed.addAll(subbed)
        linksBox.put(links)
    }

    private fun getSubbedLinks(): List<String> {
        val links = mainLinks
        return if (links.subbed != null) {
            links.subbed
        } else {
            emptyList()
        }
    }

    fun setCartoonLinks(cartoons: List<String>) {
        val links = mainLinks
        links.cartoons.clear()
        links.cartoons.addAll(cartoons)
        linksBox.put(links)
    }

    private fun getCartoonsLinks(): List<String> {
        val links = mainLinks
        return if (links.cartoons != null) {
            links.cartoons
        } else {
            emptyList()
        }
    }

    fun setMoviesLinks(movies: List<String>) {
        val links = mainLinks
        links.movies.clear()
        links.movies.addAll(movies)
        linksBox.put(links)
    }

    private fun getMoviesLinks(): List<String> {
        val links = mainLinks
        return if (links.movies != null) {
            links.movies
        } else {
            emptyList()
        }
    }

    fun allLinks(): List<String> {
        val links = ArrayList<String>()
        links.addAll(getSubbedLinks())
        links.addAll(getDubbedLinks())
        links.addAll(getCartoonsLinks())
        links.addAll(getMoviesLinks())
        return links
    }

    fun identityForSeriesLink(link: String): SeriesIdentity {
        for (s in getSubbedLinks()) {
            if (s == link) {
                return SeriesIdentity.SUBBED
            }
        }
        for (s in getDubbedLinks()) {
            if (s == link) {
                return SeriesIdentity.DUBBED
            }
        }
        for (s in getCartoonsLinks()) {
            if (s == link) {
                return SeriesIdentity.CARTOON
            }
        }
        for (s in getMoviesLinks()) {
            if (s == link) {
                return SeriesIdentity.MOVIE
            }
        }
        return SeriesIdentity.NONE
    }

    @Suppress("UNUSED")
    //used to fix mistakes I made while organizing the images lol
    fun fixImageNames() {
        val saveFolder = File(databasePath + "SeriesImages" + File.separator)
        val images = saveFolder.listFiles()
        if (images != null) {
            for (f in images) {
                val title = f.name
                if (title.endsWith(".jpg.jpg")) {
                    println("Found double extension file: $title")
                    val save = File(saveFolder.absolutePath
                            + File.separator + title.substringBefore(".jpg.jpg") + ".jpg")
                    f.renameTo(save)
                    println("Renamed to ${save.absolutePath}")
                    //f.delete()
                    //println("Successfully deleted copied image.")
                    /*try {
                        f.renameTo(File(saveFolder.absolutePath
                                + File.separator + title + ".jpg"))
                        println("Successfully fixed image with no extension")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }*/
                }
                /*val title = f.name
                val subKeyword = "English Subbed"
                val dubKeyword = "English Dubbed"
                if (title.contains(subKeyword) || title.contains(dubKeyword)) {
                    val fixedName = Tools.stripExtraFromTitle(f.name)
                    val path = File(
                        saveFolder.absolutePath + File.separator +
                                fixedName + ".jpg"
                    ).toPath()
                    try {
                        Files.copy(f.toPath(), path, StandardCopyOption.REPLACE_EXISTING)
                        f.delete()
                        println("Successfully fixed image name and deleted old one.")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }*/
            }
        }
    }

    @Suppress("UNUSED")
    suspend fun downloadSeriesImages() {
        val saveFolder = File(seriesImagesPath)
        if (!saveFolder.exists() && !saveFolder.mkdir()) {
            println("Unable to download series images. Save folder was unable to be created.")
            return
        }
        val failedDownloads = ArrayList<Series>()
        println("Checking ${seriesBox.all.size} series for undownloaded images.")
        for ((index, series) in seriesBox.all.withIndex()) {
            val saveFile = File(
                "${saveFolder.absolutePath}${File.separator}" +
                        Tools.titleForImages(series.name)
            )
            if (!saveFile.parentFile.exists()) {
                saveFile.parentFile.mkdir()
            }
            if (!saveFile.exists()) {
                try {
                    downloadFile(
                        series.imageLink,
                        saveFile,
                        model.settings().integerSetting(Defaults.TIMEOUT) * 1000,
                        model.randomUserAgent
                    )
                    println("Successfully downloaded image: ${series.imageLink} Index: $index")
                } catch (e: Exception) {
                    failedDownloads.add(series)
                    println("Failed to download image. Error: ${e.localizedMessage}")
                }
            }
        }
        println("Successfully downloaded all images with ${failedDownloads.size} failed ones.")
        if (failedDownloads.isNotEmpty()) {
            println("Attempting to download failed images.")
            for (series in failedDownloads) {
                val saveFile = File(
                    "${saveFolder.absolutePath}${File.separator}" +
                            Tools.titleForImages(series.name)
                )
                try {
                    downloadFile(
                        series.imageLink,
                        saveFile,
                        model.settings().integerSetting(Defaults.TIMEOUT) * 1000,
                        model.randomUserAgent
                    )
                    println("Successfully downloaded image: ${series.imageLink}")
                } catch (e: Exception) {
                    println("Failed to download image. Error: ${e.localizedMessage}")
                }
            }
        }
    }

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

    fun removeSeries(series: Series) {
        seriesBox.remove(series)
    }

    fun seriesForLink(link: String): Series? {
        try {
            seriesBox.query()
                .equal(Series_.link, link, StringOrder.CASE_SENSITIVE)
                .or()
                .equal(Series_.movieLink, link, StringOrder.CASE_SENSITIVE)
                .build()
                .use { query -> return query.findUnique() }
        } catch (ignored: Exception) {
        }
        return null
    }

    @Suppress("UNUSED")
    fun seriesForName(name: String): Series? {
        try {
            seriesBox.query()
                .equal(Series_.name, name, StringOrder.CASE_SENSITIVE).build()
                .use { query -> return query.findUnique() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}