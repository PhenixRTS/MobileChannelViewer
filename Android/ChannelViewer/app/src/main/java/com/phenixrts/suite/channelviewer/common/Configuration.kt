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
import com.phenixrts.suite.phenixdeeplink.common.ChannelConfiguration
import timber.log.Timber

fun getChannelConfiguration(channelAlias: String, surface: AndroidVideoRenderSurface,
                            channelConfig: ChannelConfiguration): JoinChannelOptions {
    var joinRoomBuilder = RoomExpressFactory.createJoinRoomOptionsBuilder()
        .withRoomAlias(channelAlias)
    if (channelConfig.edgeToken.isNullOrBlank()) {
        joinRoomBuilder = joinRoomBuilder.withCapabilities(arrayOf("real-time"))
    }
    val joinRoomOptions = joinRoomBuilder.buildJoinRoomOptions()
    val rendererOptions = RendererOptions().apply {
        aspectRatioMode = AspectRatioMode.LETTERBOX
    }
    var joinChannelBuilder = ChannelExpressFactory
        .createJoinChannelOptionsBuilder()
        .withJoinRoomOptions(joinRoomOptions)
        .withRenderer(surface)
        .withRendererOptions(rendererOptions)
    if (!channelConfig.edgeToken.isNullOrBlank()) {
        Timber.d("Joining with edge token: ${channelConfig.edgeToken}")
        joinChannelBuilder = joinChannelBuilder
                .withStreamToken(channelConfig.edgeToken)
                .withSkipRetryOnUnauthorized()
    }
    return joinChannelBuilder.buildJoinChannelOptions()
}
