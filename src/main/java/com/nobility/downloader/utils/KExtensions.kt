package com.nobility.downloader.utils

import org.apache.commons.lang3.StringUtils

fun String.ordinalIndexOf(searchString: String, ordinal: Int): Int {
    return StringUtils.ordinalIndexOf(this, searchString, ordinal)
}