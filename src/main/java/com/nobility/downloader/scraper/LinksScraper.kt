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
        for (identity in SeriesIdentity.filteredValues()) {
            try {
                scrapeLinks(identity)
            } catch (e: Exception) {
                println("An error occured when scraping links for $identity. " +
                        "Error: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun scrapeLinks(
        identity: SeriesIdentity
    ) = withContext(Dispatchers.IO) {
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
        val added = model.settings().addLinks(links, identity)
        if (added > 0) {
            when (identity) {
                SeriesIdentity.SUBBED -> {
                    println("Successfully downloaded $added missing subbed links.")
                }
                SeriesIdentity.DUBBED -> {
                    println("Successfully downloaded $added missing dubbed links.")
                }
                SeriesIdentity.CARTOON -> {
                    println("Successfully downloaded $added missing cartoon links.")
                }
                SeriesIdentity.MOVIE -> {
                    println("Successfully downloaded $added missing movie links.")
                }
                else -> {}
            }
        }
    }

    fun findIdentityForUrl(url: String): SeriesIdentity {
        println("Looking for identity for $url online...")
        for (identity in SeriesIdentity.filteredValues()) {
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
            if (links.contains(url)) {
                model.settings().addLink(url, identity)
                println("Successfully labeled $url as $identity")
                return identity
            } else {
                model.settings().addLink(url, SeriesIdentity.NEW)
                println("Failed to find identity. Caching $url with identity ${SeriesIdentity.NEW}")
                return SeriesIdentity.NEW
            }
        }
        return SeriesIdentity.NONE
    }

}