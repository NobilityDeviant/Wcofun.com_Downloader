package com.nobility.downloader.scraper

import com.nobility.downloader.Model
import com.nobility.downloader.driver.DriverBase
import com.nobility.downloader.entities.SeriesIdentity
import com.nobility.downloader.utils.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Used to scrape wco links and take the slugs from those links.
 * These slugs will be used to identify series.
 */
class IdentityScraper(model: Model) : DriverBase(model) {

    suspend fun scrapeAllLinksForSlugs() {
        for (identity in SeriesIdentity.filteredValues()) {
            try {
                scrapeLinksToSlugs(identity)
            } catch (e: Exception) {
                println(
                    "An error occured when scraping links for $identity. " +
                            "Error: ${e.localizedMessage}"
                )
                e.printStackTrace()
            }
        }
    }

    private suspend fun scrapeLinksToSlugs(
        identity: SeriesIdentity
    ) = withContext(Dispatchers.IO) {
        val fullLink = model.linkForSlug(identity.slug)
        val slugs = mutableListOf<String>()
        driver.get(fullLink)
        val doc = Jsoup.parse(driver.pageSource)
        val list = doc.getElementsByClass("ddmcc")
        val ul = list.select("ul")
        for (uls in ul) {
            val lis = uls.select("li")
            for (li in lis) {
                var s = li.select("a").attr("href")
                if (s.contains("//")) {
                    s = Tools.extractSlugFromLink(s)
                }
                if (s.startsWith("/")) {
                    s = s.replaceFirst("/", "")
                }
                System.err.println("Identity: $s")
                slugs.add(s)
            }
        }
        if (slugs.isEmpty()) {
            println("Failed to find link for $identity")
            return@withContext
        }
        val added = model.settings().addCategoryLinksWithSlug(slugs, identity)
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

    fun findIdentityForSeriesSlug(
        slug: String
    ): SeriesIdentity {
        println("Looking for identity for ${model.wcoUrl + slug} online...")
        for (identity in SeriesIdentity.filteredValues()) {
            val fullLink = model.linkForSlug(identity.slug)
            val slugs = mutableListOf<String>()
            driver.get(fullLink)
            val doc = Jsoup.parse(driver.pageSource)
            val list = doc.getElementsByClass("ddmcc")
            val ul = list.select("ul")
            for (uls in ul) {
                val lis = uls.select("li")
                for (li in lis) {
                    var s = li.select("a").attr("href")
                    if (s.contains("//")) {
                        s = Tools.extractSlugFromLink(s)
                    }
                    if (s.startsWith("/")) {
                        s = s.replaceFirst("/", "")
                    }
                    System.err.println("Find Identity: $s")
                    slugs.add(s)
                }
            }
            if (slugs.contains(slug)) {
                model.settings().addCatgeoryLinkWithSlug(slug, identity)
                println("Successfully labeled $fullLink as $identity")
                return identity
            }
        }
        model.settings().addCatgeoryLinkWithSlug(slug, SeriesIdentity.NEW)
        //println("Failed to find identity. Caching $fullLink with identity: ${SeriesIdentity.NEW}")
        return SeriesIdentity.NEW
    }

}