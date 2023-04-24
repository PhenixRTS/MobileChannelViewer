package com.phenixrts.suite.phenixdeeplink.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject

const val QUERY_EDGE_TOKEN = "edgeToken"
const val QUERY_AUTH_TOKEN = "authToken"
const val QUERY_PUBLISH_TOKEN = "publishToken"
const val QUERY_MIME_TYPES = "mimetypes"

@Serializable
data class ChannelConfiguration(
    @SerialName(QUERY_EDGE_TOKEN)
    val edgeToken: String? = null,
    @SerialName(QUERY_AUTH_TOKEN)
    val authToken: String? = null,
    @SerialName(QUERY_PUBLISH_TOKEN)
    val publishToken: String? = null,
    @SerialName(QUERY_MIME_TYPES)
    private val rawMimeTypes: String? = null
) {
    val mimeTypes: List<String> = rawMimeTypes?.split(",") ?: listOf()
}

fun HashMap<String, String>.asConfigurationModel(): ChannelConfiguration? = try {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }.decodeFromString<ChannelConfiguration>(JSONObject(this as Map<*, *>).toString())
} catch (e: Exception) {
    null
}
