package com.nobility.downloader.scraper

data class RecentResult(val data: List<Data>) {
    data class Data(val imagePath: String, val name: String, val link: String, val isSeries: Boolean)
}