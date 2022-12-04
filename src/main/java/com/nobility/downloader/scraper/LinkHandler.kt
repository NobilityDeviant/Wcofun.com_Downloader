package com.nobility.downloader.scraper

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.entities.Genre
import com.nobility.downloader.entities.Series
import com.nobility.downloader.entities.SeriesIdentity
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList

class LinkHandler(model: Model) : DriverBase(model) {

    suspend fun handleLink(link: String, forceSeries: Boolean = false): Resource<Any> {
        println("Scraping data from url: $link")
        val isSeriesResult = isSeriesOrEpisode(link)
        if (isSeriesResult.message != null) {
            return Resource.Error(isSeriesResult.message)
        }
        val isSeries = isSeriesResult.data == true
        if (forceSeries or isSeries) {
            val linksScraper = LinksScraper(model)
            if (model.settings().areLinksEmpty()) {
                println("Links are empty. Please wait while they download...")
                linksScraper.scrapeAllLinks()
            }
            var identity = model.settings().identityForSeriesUrl(link)
            if (identity == SeriesIdentity.NONE) {
                identity = linksScraper.findIdentityForUrl(link)
            }
            linksScraper.killDriver()
            val series = scrapeSeries(
                link,
                identity.type
            )
            if (series.data != null) {
                return Resource.Success(series.data)
            } else if (series.message != null) {
                return Resource.Error(series.message)
            }
        } else {
            val episode = scrapeEpisodeOrSeries(link)
            if (episode.data != null) {
                return Resource.Success(episode.data)
            } else if (episode.message != null) {
                return Resource.Error(episode.message)
            }
        }
        return Resource.Error("Something weird went wrong.")
    }

    suspend fun handleSeriesLinks(seriesLinks: List<String>, id: Int) {
        println("Scraping data for ${seriesLinks.size} series for id: $id.")
        for (link in seriesLinks) {
            delay(500)
            if (model.settings().wcoHandler.seriesForLink(link) != null) {
                println("Skipping cached series: $link for id: $id")
                continue
            }
            val linksScraper = LinksScraper(model)
            if (model.settings().areLinksEmpty()) {
                println("Links are empty. Please wait while they download...")
                linksScraper.scrapeAllLinks()
            }
            var identity = model.settings().identityForSeriesUrl(link)
            if (identity == SeriesIdentity.NONE) {
                identity = linksScraper.findIdentityForUrl(link)
            }
            linksScraper.killDriver()
            println("Checking $link for id: $id")
            val series = scrapeSeries(link, identity.type)
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
    private suspend fun isSeriesOrEpisode(link: String): Resource<Boolean> = withContext(Dispatchers.IO) {
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
            return@withContext Resource.Error("Failed to load $link Error: ${e.localizedMessage}")
        }
    }

    suspend fun getSeriesEpisodes(seriesLink: String): Resource<List<Episode>> = withContext(Dispatchers.IO) {
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
                    val episodeLink = element.select("a").attr("href")
                    val episode = Episode(
                        episodeTitle,
                        episodeLink,
                        seriesLink
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

    private suspend fun scrapeSeries(seriesLink: String, identity: Int): Resource<Series> = withContext(Dispatchers.IO) {
        try {
            val series: Series
            val episodes = ArrayList<Episode>()
            var movieLink = ""
            driver.get(seriesLink)
            var doc = Jsoup.parse(driver.pageSource)
            if (identity == SeriesIdentity.MOVIE.type) {
                val category = doc.getElementsByClass("header-tag")
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = h2.text()
                if (categoryName.lowercase(Locale.getDefault()) == "movies") {
                    val videoTitle = doc.getElementsByClass("video-title")
                    series = Series(
                        seriesLink,
                        videoTitle[0].text(),
                        "",
                        "",
                        Tools.dateFormatted,
                        identity
                    )
                    model.settings().seriesBox.attach(series)
                    model.settings().wcoHandler.seriesBox.attach(series)
                    series.movieLink = categoryLink
                    series.episodes.add(Episode(videoTitle[0].text(), seriesLink, ""))
                    return@withContext Resource.Success(series)
                } else {
                    movieLink = categoryLink
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
                        episodeLink,
                        seriesLink
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
                                    linkElement
                                )
                            )
                        }
                    }
                }
                series = Series(
                    seriesLink,
                    title,
                    imageLink,
                    descriptionText,
                    Tools.dateFormatted,
                    identity
                )
                model.settings().seriesBox.attach(series)
                model.settings().wcoHandler.seriesBox.attach(series)
                series.movieLink = movieLink
                model.settings().wcoHandler.downloadSeriesImage(series)
                series.episodes.addAll(episodes)
                series.genres.addAll(genresList)
                return@withContext Resource.Success(series)
            } else {
                throw Exception("No episodes were found.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }

    private suspend fun scrapeEpisodeOrSeries(episodeLink: String): Resource<ToDownload> = withContext(Dispatchers.IO) {
        try {
            driver.get(episodeLink)
            val doc = Jsoup.parse(driver.pageSource)
            val episodeTitle = doc.getElementsByClass("video-title")
            val category = doc.getElementsByClass("header-tag") //category is the series
            var seriesLink = ""
            if (!category.isEmpty()) {
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = h2.text()
                seriesLink = categoryLink
                if (categoryName.isNotEmpty() && seriesLink.isNotEmpty()) {
                    try {
                        println("Looking for series from episode link ($episodeLink).")
                        val result = handleLink(seriesLink, true)
                        if (result.data is Series) {
                            val added = model.settings().addOrUpdateSeries(result.data)
                            val added1 = model.settings().wcoHandler.addOrUpdateSeries(result.data)
                            if (added || added1) {
                                println("Added series found from episode link ($episodeLink) successfully.")
                            }
                            return@withContext Resource.Success(ToDownload(
                                result.data,
                                result.data.episodeForLink(episodeLink)
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
                    episodeLink,
                    seriesLink
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