package com.nobility.downloader.series

import com.google.common.collect.Lists
import com.nobility.downloader.Model
import com.nobility.downloader.driver.DriverBase
import com.nobility.downloader.entities.CategoryLink
import com.nobility.downloader.scraper.IdentityScraper
import com.nobility.downloader.scraper.SlugHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesScraper(model: Model) : DriverBase(model) {

    suspend fun updateWcoDb() = withContext(Dispatchers.IO) {
        val identityLinkScraper = IdentityScraper(model)
        identityLinkScraper.scrapeAllLinksForSlugs()
        identityLinkScraper.killDriver()
        val allLinks = model.settings().allCategoryLinks()
        val uncached = HashSet<CategoryLink>()
        println("[WARNING] This is a very intensive task. If wcofun's owners detect multiple bots running, " +
                "they will add cloudflare which could kill this program.")
        println("Searching through ${allLinks.size} links for uncached series.")
        for (s in allLinks) {
            if (model.settings().wcoHandler.seriesForSlug(s.slug) == null) {
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
                val slugHandler = SlugHandler(model)
                cors.add(launch {slugHandler.handleSeriesSlugs(l, id) })
            }
            model.isUpdatingWco = false
            println("Successfully finished saving all uncached series.")
        }
    }
}