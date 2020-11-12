/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.common

import com.phenixrts.express.ChannelExpressFactory
import com.phenixrts.express.JoinChannelOptions
import com.phenixrts.express.RoomExpressFactory
import com.phenixrts.pcast.AspectRatioMode
import com.phenixrts.pcast.RendererOptions
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.suite.phenixdeeplink.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
data class ChannelExpressConfiguration(
    @SerialName(QUERY_URI)
    val uri: String? = null,
    @SerialName(QUERY_BACKEND)
    val backend: String? = null,
    @SerialName(QUERY_EDGE_AUTH)
    val edgeAuth: String? = null,
    @SerialName(QUERY_CHANNEL_ALIAS)
    val channelAlias: String? = null,
    @SerialName(QUERY_MIME_TYPES)
    private val rawMimeTypes: String? = null
) {
    val mimeTypes: List<String> = rawMimeTypes?.split(",") ?: listOf()
}

fun getChannelConfiguration(channelAlias: String, surface: AndroidVideoRenderSurface): JoinChannelOptions {
    val joinRoomOptions = RoomExpressFactory.createJoinRoomOptionsBuilder()
        .withRoomAlias(channelAlias)
        .withCapabilities(arrayOf("real-time"))
        .buildJoinRoomOptions()
    val rendererOptions = RendererOptions()
    rendererOptions.aspectRatioMode = AspectRatioMode.LETTERBOX
    return ChannelExpressFactory
        .createJoinChannelOptionsBuilder()
        .withJoinRoomOptions(joinRoomOptions)
        .withRenderer(surface)
        .withRendererOptions(rendererOptions)
        .buildJoinChannelOptions()
}

fun HashMap<String, String>.asConfigurationModel(): ChannelExpressConfiguration? = try {
    Json { ignoreUnknownKeys = true }.decodeFromString<ChannelExpressConfiguration>(JSONObject(this as Map<*, *>).toString())
} catch (e: Exception) {
    null
}
