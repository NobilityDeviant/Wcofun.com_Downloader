package com.nobility.downloader.cache

class WebsiteData {

    private val categories = mutableListOf<Category>()
    var lastUpdate: Long = 0

    fun addCategory(category: Category, replace: Boolean): Boolean {
        if (categoryForLink(category.url) == null) {
            categories.add(category)
            return true
        } else if (replace) {
            return replaceCategory(category)
        }
        return false
    }

    private fun replaceCategory(category: Category): Boolean {
        for ((index, value) in categories.withIndex()) {
            if (value.name == category.name) {
                if (!areCategoriesEqual(value, category)) {
                    categories.removeAt(index)
                    categories.add(category)
                    return true
                }
                break
            }
        }
        return false
    }

    private fun areCategoriesEqual(cat1: Category, cat2: Category): Boolean {
        if (cat1.url.isEmpty() || cat1.name.isEmpty()
            || cat2.url.isEmpty() || cat2.name.isEmpty()) {
            return false
        }
        return cat1.url == cat2.url && cat1.name == cat2.name
    }

    private fun categoryForLink(url: String): Category? {
        for (c in categories) {
            if (c.url == url) {
                return c
            }
        }
        return null
    }
}