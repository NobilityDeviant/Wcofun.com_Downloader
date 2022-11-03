package com.nobility.downloader.scraper

import com.nobility.downloader.entities.Episode

data class DownloadEpisode(val episode: Episode, var selected: Boolean = false)