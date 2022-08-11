/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdeeplinks.models

import com.phenixrts.suite.phenixdeeplinks.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

internal const val STAGING_URI = "stg.phenixrts.com"
internal const val QUERY_AUTH_TOKEN = "authToken"
internal const val QUERY_EDGE_TOKEN = "edgeToken"
internal const val QUERY_PUBLISH_TOKEN = "publishToken"
internal const val QUERY_TOKEN = "token"
internal const val QUERY_URI = "uri"
internal const val QUERY_ACTS = "acts"
internal const val QUERY_MIME_TYPES = "mimeTypes"
internal const val QUERY_URL = "url"
internal const val QUERY_VIDEO_COUNT = "maxVideoMembers"
internal const val QUERY_STREAM_IDS = "streamIDs"
internal const val QUERY_CHANNEL_ALIASES = "channelAliases"
internal const val QUERY_CHANNEL_TOKENS = "channelTokens"
internal const val QUERY_ROOM_ALIASES = "roomAliases"
internal const val QUERY_ROOM_AUDIO_TOKEN = "roomAudioToken"
internal const val QUERY_ROOM_VIDEO_TOKEN = "roomVideoToken"
internal const val QUERY_SELECTED_ALIAS = "selectedAlias"
internal const val QUERY_PUBLISHING_ENABLED = "publishingEnabled"
internal const val QUERY_CHANNEL = "#"

@Suppress("unused")
@Serializable
data class PhenixDeepLinkConfiguration(
    @SerialName(QUERY_AUTH_TOKEN) val authToken: String,
    @SerialName(QUERY_EDGE_TOKEN) val edgeToken: String,
    @SerialName(QUERY_PUBLISH_TOKEN) val publishToken: String,
    @SerialName(QUERY_TOKEN) val token: String,
    @SerialName(QUERY_URI) val uri: String,
    @SerialName(QUERY_ACTS) val rawActs: String,
    @SerialName(QUERY_MIME_TYPES) val rawMimeTypes: String,
    @SerialName(QUERY_URL) val url: String,
    @SerialName(QUERY_VIDEO_COUNT) private val maxVideoMembers: String,
    @SerialName(QUERY_STREAM_IDS) val rawStreamIDs: String,
    @SerialName(QUERY_CHANNEL_ALIASES) val rawChannelAliases: String,
    @SerialName(QUERY_CHANNEL_TOKENS) val rawChannelTokens: String,
    @SerialName(QUERY_ROOM_ALIASES) val rawRoomAliases: String,
    @SerialName(QUERY_ROOM_AUDIO_TOKEN) val roomAudioToken: String,
    @SerialName(QUERY_ROOM_VIDEO_TOKEN) val roomVideoToken: String,
    @SerialName(QUERY_SELECTED_ALIAS) val selectedAlias: String,
    @SerialName(QUERY_PUBLISHING_ENABLED) val rawPublishingEnabled: String,
) {
    @Transient val acts = rawActs.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
    @Transient val mimeTypes = rawMimeTypes.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
    @Transient val channels = rawChannelAliases.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
    @Transient val rooms = rawRoomAliases.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
    @Transient val streams = rawStreamIDs.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
    @Transient val channelTokens = rawChannelTokens.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
    @Transient val videoMemberCount = maxVideoMembers.takeIf { it.isNotBlank() }?.toIntOrNull() ?: BuildConfig.MAX_VIDEO_RENDERERS
    @Transient val publishingEnabled = rawPublishingEnabled.toBooleanStrictOrNull() ?: false
}
