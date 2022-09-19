package com.nobility.downloader.scraper

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.cache.Episode
import com.nobility.downloader.cache.Genre
import com.nobility.downloader.cache.Series
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.utils.Tools.fixTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class LinkScraper(model: Model) : DriverBase(model) {

    suspend fun handleLink(url: String, forceSeries: Boolean = false): Resource<Any> {
        println("Scraping data from url: $url")
        val isSeriesResult = isSeriesOrEpisode(url)
        if (isSeriesResult.message != null) {
            return Resource.Error(isSeriesResult.message)
        }
        val isSeries = isSeriesResult.data!!
        if (forceSeries or isSeries) {
            val series = scrapeSeries(url)
            if (series.data != null) {
                return Resource.Success(series.data)
            } else if (series.message != null) {
                return Resource.Error(series.message)
            }
        } else {
            val episode = scrapeEpisode(url)
            if (episode.data != null) {
                return Resource.Success(episode.data)
            } else if (episode.message != null) {
                return Resource.Error(episode.message)
            }
        }
        return Resource.Error("Something weird went wrong.")
    }

    /**
     * Returns true for series, false for episode or null for an error.
     */
    private suspend fun isSeriesOrEpisode(url: String): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            driver.get(url)
            val doc = Jsoup.parse(driver.pageSource)
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Page not found.")
            }
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            return@withContext Resource.Success(categoryEpisodes.isNotEmpty())
        } catch (e: Exception) {
            return@withContext Resource.Error("Failed to load $url Error: ${e.localizedMessage}")
        }
    }

    suspend fun getSeriesEpisodes(url: String): Resource<List<Episode>> = withContext(Dispatchers.IO) {
        try {
            driver.get(url)
            val doc = Jsoup.parse(driver.pageSource)
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Page not found.")
            }
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            if (categoryEpisodes.isNotEmpty()) {
                val episodes = ArrayList<Episode>()
                categoryEpisodes.reverse()
                for (element in categoryEpisodes) {
                    val title = element.select("a").text()
                    val link = element.select("a").attr("href")
                    val episode = Episode(
                        fixTitle(title),
                        link,
                        url
                    )
                    episodes.add(episode)
                }
                return@withContext Resource.Success(episodes)
            }
        } catch (e: Exception) {
            return@withContext Resource.Error("Failed to load $url Error: ${e.localizedMessage}")
        }
        return@withContext Resource.Error("Failed to find any episodes for $url")
    }

    private suspend fun scrapeSeries(url: String): Resource<Series> = withContext(Dispatchers.IO) {
        try {
            val series: Series
            val episodes = ArrayList<Episode>()
            driver.get(url)
            val doc = Jsoup.parse(driver.pageSource)
            val videoTitle = doc.getElementsByClass("video-title")
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            if (categoryEpisodes.isNotEmpty()) {
                categoryEpisodes.reverse()
                for (element in categoryEpisodes) {
                    val title = element.select("a").text()
                    val link = element.select("a").attr("href")
                    val episode = Episode(
                        fixTitle(title),
                        link,
                        url
                    )
                    episodes.add(episode)
                }
                var title = ""
                if (videoTitle.isNotEmpty()) {
                    title = fixTitle(videoTitle[0].text())
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
                        val link = genre.attr("href")
                        if (link.contains("search-by-genre")) {
                            genresList.add(
                                Genre(
                                    genre.text(),
                                    link
                                )
                            )
                        }
                    }
                }
                series = Series(
                    url,
                    title,
                    imageLink,
                    descriptionText,
                    episodes,
                    genresList,
                    Tools.dateFormatted
                )
                return@withContext Resource.Success(series)
            } else {
                throw Exception("No episodes were found.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        } finally {
            killDriver()
        }
    }

    private suspend fun scrapeEpisode(url: String): Resource<Episode> = withContext(Dispatchers.IO) {
        try {
            driver.get(url)
            val doc = Jsoup.parse(driver.pageSource)
            val videoTitle = doc.getElementsByClass("video-title")
            val category = doc.getElementsByClass("header-tag")
            var seriesLink = ""
            if (!category.isEmpty()) {
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = fixTitle(h2.text())
                seriesLink = categoryLink
                if (categoryName.isNotEmpty() && seriesLink.isNotEmpty()) {
                    model.backgroundScope.launch(Dispatchers.IO) {
                        try {
                            println("Looking for series from episode link ($url) in the background.")
                            val series = handleLink(seriesLink, true)
                            if (series.data is Series) {
                                val added = model.history().addSeries(series.data, true)
                                if (added) {
                                    model.saveSeriesHistory()
                                    println("Added series found from episode link ($url) successfully.")
                                }
                            }
                        } catch (e: Exception) {
                            println("Failed to find series for ($url). Error: ${e.localizedMessage}")
                        }
                    }
                }
            }
            if (videoTitle.isNotEmpty()) {
                val episode = Episode(
                    fixTitle(videoTitle[0].text()),
                    url,
                    seriesLink
                )
                //println("Found episode " + episode.name
                  //      + " " + if (seriesLink.isNotEmpty()) "| Category: $seriesLink" else "")
                return@withContext Resource.Success(episode)
            } else {
                return@withContext Resource.Error("Failed to find the episodes's title.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }
}