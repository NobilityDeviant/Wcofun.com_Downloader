package com.nobility.downloader.utils

sealed class Resource<T>(val data: T? = null, val message: String? = null, val errorCode: Int? = -1) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String) : Resource<T>(message = message) {
        constructor(message: String, exception: Exception? = null): this(
            "$message | Error: " + if (exception != null)
                exception.localizedMessage else "No error found."
        )
        constructor(exception: Exception? = null) : this("Error: " + if (exception != null)
            exception.localizedMessage else "No error found.")
    }

    class ErrorCode<T>(message: String, errorCode: Int?) : Resource<T>(message = message, errorCode = errorCode) {
        constructor(message: String, errorMessage: String? = "An unknown error has occured.", errorCode: Int?):
                this("${if (message.isNotEmpty()) "$message\n" else ""}Error: $errorMessage\nError Code: $errorCode", errorCode)
        constructor(exception: Exception?, errorCode: Int?) : this((exception?.localizedMessage?: "An unknown error has occured.") + "\nError Code: $errorCode", errorCode)
        constructor(message: String, exception: Exception?, errorCode: Int?) : this(message + "\nError: " + (exception?.localizedMessage?: "An unknown error has occured.") + "\nError Code: $errorCode", errorCode)
        constructor(errorCode: Int?): this("", errorCode)
    }
}