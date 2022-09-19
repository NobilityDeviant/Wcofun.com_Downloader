package com.nobility.downloader.scraper

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.cache.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class CategoryUpdater(model: Model) : DriverBase(model) {

    suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        val lastUpdate = model.data().lastUpdate
        if (lastUpdate > 0) {
            val now = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
            val last = TimeUnit.MILLISECONDS.toDays(lastUpdate)
            println("Now: $now Last: $last Calc: ${now.minus(last)}")
            if (last > 0 && now.minus(last) < 30L) {
                return@withContext
            }
        }
        println("Updating latest categories.")
        driver.get(Model.WEBSITE)
        val doc = Jsoup.parse(driver.pageSource)
        val categoryElements = doc.getElementsByClass("cerceve")
        if (categoryElements.isNotEmpty()) {
            for (category in categoryElements) {
                val added = model.data().addCategory(
                    Category(category.attr("href"), category.text()), true
                )
                if (added) {
                    println("Found category ${category.attr("href")} | ${category.text()}")
                }
            }
            model.data().lastUpdate = System.currentTimeMillis()
            model.saveData()
        }
    }

}