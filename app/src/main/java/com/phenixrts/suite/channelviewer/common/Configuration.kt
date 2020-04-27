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
import com.phenixrts.suite.channelviewer.BuildConfig
import java.io.Serializable

const val QUERY_URI = "uri"
const val QUERY_BACKEND = "backend"
const val QUERY_CHANNEL = "#"

data class ChannelConfiguration(
    val uri: String = BuildConfig.PCAST_URL,
    val backend: String = BuildConfig.BACKEND_URL
) : Serializable

fun getChannelConfiguration(channelCode: String, surface: AndroidVideoRenderSurface): JoinChannelOptions {
    val joinRoomOptions = RoomExpressFactory.createJoinRoomOptionsBuilder()
        .withRoomAlias(channelCode)
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
