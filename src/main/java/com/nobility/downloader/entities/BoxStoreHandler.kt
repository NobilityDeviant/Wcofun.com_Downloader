package com.nobility.downloader.entities

import com.nobility.downloader.Model
import com.nobility.downloader.entities.settings.SettingsMeta
import com.nobility.downloader.entities.settings.SettingsMeta_
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.settings.Quality
import io.objectbox.Box
import io.objectbox.query.QueryBuilder.StringOrder
import java.io.File

class BoxStoreHandler(model: Model) {

    val wcoHandler: WcoHandler
    private val databasePath = "./database/"
    private val myData = MyObjectBox.builder()
        .directory(File(databasePath + "mydata"))
        .build()
    private val linksData = MyObjectBox.builder()
        .directory(File(databasePath + "links"))
        .build()
    val downloadBox: Box<Download> = myData.boxFor(Download::class.java)
    val seriesBox: Box<Series> = myData.boxFor(Series::class.java)
    private val metaBox = myData.boxFor(SettingsMeta::class.java)
    private val linksBox = linksData.boxFor(CategoryLink::class.java)

    init {
        wcoHandler = WcoHandler(databasePath, model)

        downloadBox.query().filter {
            it.slug.isNullOrEmpty() || it.seriesSlug.isNullOrEmpty()
        }.build().use { query ->
            try {
                val downloads = query.find()
                if (downloads.isNotEmpty()) {
                    downloads.forEach {
                        it.updateSlugs()
                        downloadBox.put(it)
                    }
                }
            } catch (_: Exception) {}
        }
        seriesBox.query().filter {
            it.slug.isNullOrEmpty()
        }.build().use { query ->
            try {
                val series = query.find()
                if (series.isNotEmpty()) {
                    val episodesBox: Box<Episode> = myData.boxFor(Episode::class.java)
                    series.forEach {
                        it.updateSlug()
                        it.episodes.forEach { episode ->
                            episode.updateSlugs()
                            episodesBox.put(episode)
                        }
                        it.genres.forEach { genre ->
                            genre.updateSlug()
                        }
                        seriesBox.put(it)
                    }
                }
            } catch (_: Exception) {}
        }
        wcoHandler.seriesBox.query().filter {
            it.slug.isNullOrEmpty()
        }.build().use { query ->
            try {
                val series = query.find()
                if (series.isNotEmpty()) {
                    val episodesBox: Box<Episode> = wcoHandler.wcoData.boxFor(
                        Episode::class.java
                    )
                    series.forEach {
                        it.updateSlug()
                        it.episodes.forEach { episode ->
                            episode.updateSlugs()
                            episodesBox.put(episode)
                        }
                        it.genres.forEach { genre ->
                            genre.updateSlug()
                        }
                        wcoHandler.seriesBox.put(it)
                    }
                }
            } catch (_: Exception) {}
        }
        linksBox.query().filter {
            it.slug.isNullOrEmpty()
        }.build().use { query ->
            try {
                val links = query.find()
                if (links.isNotEmpty()) {
                    links.forEach {
                        it.updateSlug()
                        linksBox.put(it)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadSettings() {
        if (metaBox.isEmpty) {
            loadDefaultSettings()
        } else {
            checkForNewSettings()
        }
    }

    private fun setSetting(key: String, value: Any?) {
        var metA: SettingsMeta? = metaForKey(key)
        if (metA != null) {
            metA.valueObj = value
        } else {
            metA = SettingsMeta()
            metA.key = key
            metA.valueObj = value
        }
        metaBox.put(metA)
    }

    fun setSetting(setting: Defaults, value: Any) {
        setSetting(setting.key, value)
    }

    private fun metaForKey(key: String): SettingsMeta? {
        try {
            metaBox.query()
                .equal(SettingsMeta_.key, key, StringOrder.CASE_SENSITIVE).build().use { query ->
                    if (query.findFirst() != null) {
                        return query.findFirst()
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun stringSetting(setting: Defaults): String {
        val meta = metaForKey(setting.key)
        return if (meta != null) {
            meta.stringVal()
        } else {
            ""
        }
    }

    fun booleanSetting(setting: Defaults): Boolean {
        val meta = metaForKey(setting.key)
        return if (meta != null) {
            meta.booleanVal()
        } else {
            false
        }
    }

    fun integerSetting(setting: Defaults): Int {
        val meta = metaForKey(setting.key)
        return if (meta != null) {
            meta.intVal()
        } else {
            0
        }
    }

    fun doubleSetting(setting: Defaults): Double {
        val meta = metaForKey(setting.key)
        return if (meta != null) {
            meta.doubleVal()
        } else {
            0.0
        }
    }

    private fun loadDefaultSettings() {
        for (setting in Defaults.values()) {
            setSetting(setting.key, setting.value)
        }
    }

    private fun checkForNewSettings() {
        for (setting in Defaults.values()) {
            val meta = metaForKey(setting.key)
            if (meta == null) {
                setSetting(setting.key, setting.value)
            }
        }
    }

    fun downloadForSlugAndQuality(slug: String, quality: Quality): Download? {
        try {
            downloadBox.query()
                .equal(
                    Download_.slug,
                    slug,
                    StringOrder.CASE_SENSITIVE
                ).and().equal(
                    Download_.resolution,
                    quality.resolution.toLong()
                ).build().use { query ->
                    if (query.findFirst() != null) {
                        return query.findFirst()
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
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
                .equal(
                    Series_.name,
                    series.name,
                    StringOrder.CASE_SENSITIVE
                ).build().use { query ->
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

    fun seriesForSlug(slug: String): Series? {
        try {
            seriesBox.query()
                .equal(
                    Series_.slug,
                    slug,
                    StringOrder.CASE_SENSITIVE
                ).build()
                .use { query -> return query.findUnique() }
        } catch (ignored: Exception) {
        }
        return null
    }

    fun areCatgeoryLinksEmpty(): Boolean {
        return linksBox.isEmpty
    }

    fun allCategoryLinks(): List<CategoryLink> {
        return linksBox.all.distinctBy { it.slug }
    }

    fun addCatgeoryLinkWithSlug(
        slug: String,
        identity: SeriesIdentity
    ): Boolean {
        if (categoryLinkForSeriesSlug(slug) == null) {
            linksBox.put(CategoryLink(slug, identity.type))
            return true
        }
        return false
    }

    fun addOrUpdateCategoryLinkWithSlug(slug: String, identity: SeriesIdentity): Boolean {
        val link = categoryLinkForSeriesSlug(slug)
        return if (link == null) {
            linksBox.put(CategoryLink(slug, identity.type))
            true
        } else {
            link.slug = slug
            link.type = identity.type
            linksBox.put(link)
            true
        }
    }

    fun addCategoryLinksWithSlug(
        slugs: List<String>,
        identity: SeriesIdentity
    ): Int {
        var added = 0
        slugs.forEach {
            if (addCatgeoryLinkWithSlug(it, identity)) {
                added++
            }
        }
        return added
    }

    private fun categoryLinkForSeriesSlug(slug: String): CategoryLink? {
        try {
            linksBox.query()
                .equal(
                    CategoryLink_.slug,
                    slug,
                    StringOrder.CASE_INSENSITIVE
                ).build()
                .use {
                    return it.findUnique()
                }
        } catch (ignored: Exception) {
        }
        return null
    }

    fun identityForSeriesSlug(slug: String): SeriesIdentity {
        val wcoLink = categoryLinkForSeriesSlug(slug)
        if (wcoLink != null) {
            return SeriesIdentity.idForType(wcoLink.type)
        }
        return SeriesIdentity.NONE
    }
}