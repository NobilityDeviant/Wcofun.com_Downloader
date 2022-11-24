package com.nobility.downloader.entities.settings

interface Meta {
    var key: String?
    var valueObj: Any?
    var stringValue: String?
    var intValue: Int?
    var longValue: Long?
    var doubleValue: Double?
    var floatValue: Float?
    var booleanValue: Boolean?
}