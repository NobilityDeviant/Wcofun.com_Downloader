package com.nobility.downloader.utils

import java.lang.Exception

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    //class Loading<T>(data: T? = null) : Resource<T>(data)
    class Error<T>(message: String) : Resource<T>(message = message) {
        constructor(exception: Exception? = null) : this(if (exception != null)
            exception.localizedMessage else "No error found, but it did fail.")
    }
}