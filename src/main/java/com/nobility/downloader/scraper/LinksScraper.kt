package com.nobility.downloader.scraper

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.entities.SeriesIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Used to scrape wco links
 * These links will be used to identify series.
 */
class LinksScraper(model: Model) : DriverBase(model) {

    suspend fun scrapeAllLinks() {
        for (identity in SeriesIdentity.values()) {
            try {
                scrapeLinks(identity)
            } catch (e: Exception) {
                println("An error occured when scraping links for $identity. " +
                        "Error: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
        killDriver()
    }

    private suspend fun scrapeLinks(
        identity: SeriesIdentity
    ) = withContext(Dispatchers.IO) {
        if (identity == SeriesIdentity.NONE) {
            return@withContext
        }
        val fullLink = Model.WEBSITE + "/" + identity.link
        val links: MutableList<String> = ArrayList()
        driver.get(fullLink)
        val doc = Jsoup.parse(driver.pageSource)
        val list = doc.getElementsByClass("ddmcc")
        val ul = list.select("ul")
        for (uls in ul) {
            val lis = uls.select("li")
            for (li in lis) {
                var s = li.select("a").attr("href")
                if (!s.startsWith(Model.WEBSITE)) {
                    s = Model.WEBSITE + s
                }
                links.add(s)
            }
        }
        if (links.isEmpty()) {
            println("Failed to find link for $identity")
            return@withContext
        }
        when (identity) {
            SeriesIdentity.SUBBED -> {
                model.settings().setSubbedLinks(links)
                println("Successfully downloaded ${links.size} subbed links.")
            }
            SeriesIdentity.DUBBED -> {
                model.settings().setDubbedLinks(links)
                println("Successfully downloaded ${links.size} dubbed links.")
            }
            SeriesIdentity.CARTOON -> {
                model.settings().setCartoonLinks(links)
                println("Successfully downloaded ${links.size} cartoon links.")
            }
            SeriesIdentity.MOVIE -> {
                model.settings().setMoviesLinks(links)
                println("Successfully downloaded ${links.size} movie links.")
            }
            else -> {}
        }
    }

}