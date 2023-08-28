package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.driver.DriverBase
import com.nobility.downloader.entities.*
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.*

class SlugHandler(model: Model) : DriverBase(model) {

    suspend fun handleSlug(
        slug: String,
        forceSeries: Boolean = false
    ): Resource<Any> {
        val fullLink = model.linkForSlug(slug)
        println("Scraping data from url: $fullLink")
        if (forceSeries) {
            return handleSeriesSlug(slug)
        }
        val isSeriesResult = isSeriesOrEpisodeWithSlug(slug)
        if (isSeriesResult.message != null) {
            return Resource.Error(isSeriesResult.message)
        }
        val isSeries = isSeriesResult.data == true
        if (isSeries) {
            return handleSeriesSlug(slug)
        } else {
            val episode = scrapeEpisodeWithSlug(slug)
            if (episode.data != null) {
                return Resource.Success(episode.data)
            } else if (episode.message != null) {
                return Resource.Error(episode.message)
            }
        }
        return Resource.Error("Something weird went wrong.")
    }

    private suspend fun handleSeriesSlug(slug: String): Resource<Any> {
        val identityScraper = IdentityScraper(model)
        if (model.settings().areCatgeoryLinksEmpty()) {
            println("Identity Links are empty. Please wait while they download...")
            identityScraper.scrapeAllLinksForSlugs()
        }
        var identity = model.settings().identityForSeriesSlug(slug)
        if (identity == SeriesIdentity.NONE) {
            identity = identityScraper.findIdentityForSeriesSlug(slug)
        }
        identityScraper.killDriver()
        val result = scrapeSeriesWithSlug(
            slug,
            identity.type
        )
        return if (result.data != null) {
            Resource.Success(result.data)
        } else if (result.message != null) {
            Resource.Error(result.message)
        } else {
            Resource.Error("Something weird went wrong.")
        }
    }

    suspend fun handleSeriesSlugs(
        categoryLinks: List<CategoryLink>,
        id: Int
    ) {
        println("Scraping data for ${categoryLinks.size} series for id: $id.")
        for ((i, cat) in categoryLinks.withIndex()) {
            delay(500)
            if (model.settings().wcoHandler.seriesForSlug(cat.slug) != null) {
                println("Skipping cached series: ${cat.slug} for id: $id")
                continue
            }
            println("Checking ${cat.slug} for index: $i out of ${categoryLinks.size}")
            val series = scrapeSeriesWithSlug(cat.slug, cat.type)
            if (series.data != null) {
                val added = model.settings().wcoHandler.addOrUpdateSeries(series.data)
                model.settings().wcoHandler.downloadSeriesImage(series.data)
                if (added) {
                    println("Successfully saved series: ${series.data.name} for id: $id")
                }
            } else if (series.message != null) {
                println(series.message + " for id: $id")
            }
        }
        killDriver()
    }

    /**
     * Returns true for series, false for episode or null for an error.
     */
    private suspend fun isSeriesOrEpisodeWithSlug(
        slug: String
    ): Resource<Boolean> = withContext(Dispatchers.IO) {
        val link = model.linkForSlug(slug)
        try {
            driver.get(link)
            val doc = Jsoup.parse(driver.pageSource)
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Page not found.")
            }
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            return@withContext Resource.Success(categoryEpisodes.isNotEmpty())
        } catch (e: Exception) {
            return@withContext Resource.Error(
                "Failed to load $link", e
            )
        }
    }

    suspend fun getSeriesEpisodesWithSlug(
        seriesSlug: String
    ): Resource<List<Episode>> = withContext(Dispatchers.IO) {
        val seriesLink = model.linkForSlug(seriesSlug)
        try {
            driver.get(seriesLink)
            val doc = Jsoup.parse(driver.pageSource)
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Page not found.")
            }
            val seriesEpisodes = doc.getElementsByClass("cat-eps")
            if (seriesEpisodes.isNotEmpty()) {
                val episodes = ArrayList<Episode>()
                seriesEpisodes.reverse()
                for (element in seriesEpisodes) {
                    val episodeTitle = element.select("a").text()
                    val episodeSlug = Tools.extractSlugFromLink(
                        element.select("a").attr("href")
                    )
                    val episode = Episode(
                        episodeTitle,
                        episodeSlug,
                        seriesSlug
                    )
                    episodes.add(episode)
                }
                return@withContext Resource.Success(episodes)
            }
        } catch (e: Exception) {
            return@withContext Resource.Error("Failed to load $seriesLink Error: ${e.localizedMessage}")
        }
        return@withContext Resource.Error("Failed to find any episodes for $seriesLink")
    }

    private suspend fun scrapeSeriesWithSlug(
        seriesSlug: String,
        identity: Int
    ): Resource<Series> = withContext(Dispatchers.IO) {
        val fullLink = model.linkForSlug(seriesSlug)
        try {
            var series = model.settings().seriesForSlug(seriesSlug)
            val episodes = mutableListOf<Episode>()
            driver.get(fullLink)
            var doc = Jsoup.parse(driver.pageSource)
            if (identity == SeriesIdentity.MOVIE.type) {
                val category = doc.getElementsByClass("header-tag")
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = h2.text()
                if (categoryName.lowercase(Locale.getDefault()) == "movies") {
                    val videoTitle = doc.getElementsByClass("video-title")
                    if (series == null) {
                        series = Series(
                            seriesSlug,
                            videoTitle[0].text(),
                            "",
                            "",
                            Tools.dateFormatted,
                            identity
                        )
                    }
                    series.updateEpisodes(listOf(Episode(videoTitle[0].text(), seriesSlug, "")))
                    return@withContext Resource.Success(series)
                } else {
                    driver.get(categoryLink)
                    doc = Jsoup.parse(driver.pageSource)
                }
            }

            val videoTitle = doc.getElementsByClass("video-title")
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            if (categoryEpisodes.isNotEmpty()) {
                categoryEpisodes.reverse()
                for (element in categoryEpisodes) {
                    val episodeTitle = element.select("a").text()
                    val episodeLink = element.select("a").attr("href")
                    val episode = Episode(
                        episodeTitle,
                        Tools.extractSlugFromLink(episodeLink),
                        seriesSlug
                    )
                    episodes.add(episode)
                }
                var title = ""
                if (videoTitle.isNotEmpty()) {
                    title = videoTitle[0].text()
                }
                val image = doc.getElementsByClass("img5")
                var imageLink = ""
                if (image.isNotEmpty()) {
                    imageLink = "https:${image.attr("src")}"
                }
                var descriptionText = ""
                val description = doc.getElementsByTag("p")
                if (description.isNotEmpty()) {
                    descriptionText = description[0].text()
                }
                val genres = doc.getElementsByClass("genre-buton")
                val genresList = mutableListOf<Genre>()
                if (genres.isNotEmpty()) {
                    for (genre in genres) {
                        val linkElement = genre.attr("href")
                        if (linkElement.contains("search-by-genre")) {
                            genresList.add(
                                Genre(
                                    genre.text(),
                                    Tools.extractSlugFromLink(linkElement)
                                )
                            )
                        }
                    }
                }
                if (series == null) {
                    series = Series(
                        seriesSlug,
                        title,
                        imageLink,
                        descriptionText,
                        Tools.dateFormatted,
                        identity
                    )
                }
                model.settings().wcoHandler.downloadSeriesImage(series)
                series.updateEpisodes(episodes)
                series.updateGenres(genresList)
                return@withContext Resource.Success(series)
            } else {
                throw Exception("No episodes were found.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }

    private suspend fun scrapeEpisodeWithSlug(
        episodeSlug: String
    ): Resource<ToDownload> = withContext(Dispatchers.IO) {
        val episodeLink = model.linkForSlug(episodeSlug)
        try {
            driver.get(episodeLink)
            val doc = Jsoup.parse(driver.pageSource)
            val episodeTitle = doc.getElementsByClass("video-title")
            val category = doc.getElementsByClass("header-tag") //category is the series
            var seriesSlug = ""
            if (!category.isEmpty()) {
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = h2.text()
                seriesSlug = Tools.extractSlugFromLink(categoryLink)
                if (categoryName.isNotEmpty() && seriesSlug.isNotEmpty()) {
                    val cachedSeries = model.settings().seriesForSlug(seriesSlug)
                    if (cachedSeries != null) {
                        return@withContext Resource.Success(ToDownload(
                            cachedSeries,
                            model.episodeForSlug(
                                cachedSeries,
                                episodeSlug
                            )
                        ))
                    }
                    try {
                        println("Looking for series from episode link ($episodeLink).")
                        val result = handleSlug(seriesSlug, true)
                        if (result.data is Series) {
                            val added = model.settings().addOrUpdateSeries(result.data)
                            val added1 = model.settings().wcoHandler.addOrUpdateSeries(result.data)
                            if (added || added1) {
                                println("Added series found from episode link ($episodeLink) successfully.")
                            }
                            return@withContext Resource.Success(ToDownload(
                                result.data,
                                model.episodeForSlug(
                                    result.data,
                                    episodeSlug
                                )
                            ))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("Failed to find series for ($episodeLink). Error: ${e.localizedMessage}")
                    }
                }
            }
            if (episodeTitle.isNotEmpty()) {
                val episode = Episode(
                    episodeTitle[0].text(),
                    episodeSlug,
                    seriesSlug
                )
                return@withContext Resource.Success(ToDownload(episode = episode))
            } else {
                return@withContext Resource.Error("Failed to find the episodes's title.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }
}