package com.nobility.downloader.entities

enum class SeriesIdentity(val slug: String, val type: Int) {
    DUBBED("dubbed-anime-list", 0),
    SUBBED("subbed-anime-list", 1),
    CARTOON("cartoon-list", 2),
    MOVIE("movie-list", 3),
    NEW("404", 4),
    NONE("404", 5);

    companion object {
        fun filteredValues(): List<SeriesIdentity> {
            return listOf(DUBBED, SUBBED, CARTOON, MOVIE)
        }
        fun idForType(type: Int): SeriesIdentity {
            for (id in values()) {
                if (id.type == type) {
                    return id
                }
            }
            return NONE
        }
    }
}