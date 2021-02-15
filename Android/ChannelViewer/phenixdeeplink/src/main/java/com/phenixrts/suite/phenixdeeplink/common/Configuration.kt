package com.phenixrts.suite.phenixdeeplink.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject

const val QUERY_STAGING = "stg.phenixrts.com"
const val QUERY_CHANNEL = "#"
const val QUERY_URI = "uri"
const val QUERY_BACKEND = "backend"
const val QUERY_EDGE_TOKEN = "edgeToken"
const val QUERY_AUTH_TOKEN = "authToken"
const val QUERY_PUBLISH_TOKEN = "publishToken"
const val QUERY_MIME_TYPES = "mimetypes"
const val QUERY_CHANNEL_ALIAS = "channelalias"

@Serializable
data class ChannelConfiguration(
    @SerialName(QUERY_URI)
    val uri: String? = null,
    @SerialName(QUERY_BACKEND)
    val backend: String? = null,
    @SerialName(QUERY_EDGE_TOKEN)
    val edgeToken: String? = null,
    @SerialName(QUERY_AUTH_TOKEN)
    val authToken: String? = null,
    @SerialName(QUERY_PUBLISH_TOKEN)
    val publishToken: String? = null,
    @SerialName(QUERY_CHANNEL_ALIAS)
    val channelAlias: String? = null,
    @SerialName(QUERY_MIME_TYPES)
    private val rawMimeTypes: String? = null
) {
    val mimeTypes: List<String> = rawMimeTypes?.split(",") ?: listOf()
}

fun HashMap<String, String>.asConfigurationModel(): ChannelConfiguration? = try {
    Json { ignoreUnknownKeys = true }.decodeFromString<ChannelConfiguration>(JSONObject(this as Map<*, *>).toString())
} catch (e: Exception) {
    null
}
