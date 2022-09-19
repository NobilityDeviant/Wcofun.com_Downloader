package com.nobility.downloader.cache

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model

class Session(model: Model) : DriverBase(model) {

    var cookies = mutableMapOf<String, String>()

    fun scrapeSession() {
        println("Loading current session...")
        driver.get(Model.WEBSITE)
        driver.manage().cookies.forEach {
            println(it.toString())
            cookies[it.name] = it.value
        }
        println("Successfully loaded session cookies and user agent.")
        println(cookies)
        killDriver()
    }

}