package com.nobility.downloader.scraper

import com.nobility.downloader.entities.Episode
import com.nobility.downloader.entities.Series

data class ToDownload(val series: Series? = null, val episode: Episode? = null)
