package com.nobility.downloader.series

import com.google.common.collect.Lists
import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.entities.SeriesIdentity
import com.nobility.downloader.scraper.LinkHandler
import com.nobility.downloader.scraper.LinksScraper
import kotlinx.coroutines.*
import org.jsoup.Jsoup

class SeriesScraper(model: Model) : DriverBase(model) {

    suspend fun updateWcoDb() = withContext(Dispatchers.IO) {
        val linksScraper = LinksScraper(model)
        linksScraper.scrapeAllLinks()
        linksScraper.killDriver()
        val allLinks = model.settings().allLinks()
        val uncached = HashSet<String>()
        println("[WARNING] This is a very intensive task. If wcofun's owners detect multiple bots running, " +
                "they will add cloudflare which could kill this program.")
        println("Searching through ${allLinks.size} links for uncached series.")
        for (s in allLinks) {
            if (model.settings().wcoHandler.seriesForLink(s) == null) {
                uncached.add(s)
            }
        }
        model.settings().wcoHandler.seriesBox.closeThreadResources()
        if (uncached.isNotEmpty()) {
            model.isUpdatingWco = true
            println("Found ${uncached.size} uncached series links. Attempting to save them all. This could take awhile...")
            val threads = 3
            val size = uncached.size / threads
            val subLists = Lists.partition(uncached.toList(), size)
            println("Running ${subLists.size} parallel coroutines (split by $size) " +
                    "and scraping each series details.")
            val cors = mutableListOf<Job>()
            for ((id, l) in subLists.withIndex()) {
                val linkHandler = LinkHandler(model)
                cors.add(launch {linkHandler.handleSeriesLinks(l, id) })
            }
            cors.joinAll()
            model.isUpdatingWco = false
            println("Successfully finished saving all uncached series.")
        }
    }

    @Suppress("UNUSED")
    suspend fun scrapeSeries() = withContext(Dispatchers.IO) {
        val identity = SeriesIdentity.CARTOON
        val fullLink = Model.WEBSITE + "/" + identity.link
        var links: MutableList<String> = ArrayList()
        driver.get(fullLink)
        val doc = Jsoup.parse(driver.pageSource)
        val list = doc.getElementsByClass("ddmcc")
        val ul = list.select("ul")
        for (uls in ul) {
            val lis = uls.select("li")
            for (li in lis) {
                var s =  li.select("a").attr("href")
                if (!s.startsWith(Model.WEBSITE)) {
                    s = Model.WEBSITE + s
                }
                links.add(s)
            }
        }
        killDriver()
        if (links.isEmpty()) {
            println("Failed to find any links for: $fullLink" +
                    "\nHere's the full source for debugging.")
            println(doc.html())
            return@withContext
        } else {
            links = ArrayList(links.distinct())
        }
        println("Successfully found ${links.size} links from $fullLink")
        val threads = 3
        val size = links.size / threads
        val subLists = Lists.partition(links, size)
        println("Running ${subLists.size} parallel coroutines (split by $size) " +
                "and scraping each series details. This might take awhile...")
        val cors = mutableListOf<Job>()
        for ((id, l) in subLists.withIndex()) {
            val linkHandler = LinkHandler(model)
            cors.add(launch {linkHandler.handleSeriesLinks(l, id) })
        }
        cors.joinAll()
        println("Successfully finished scraping series for $fullLink")
    }
}