/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
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

fun getChannelConfiguration(surface: AndroidVideoRenderSurface,
                            channelConfig: ChannelConfiguration): JoinChannelOptions {
    val rendererOptions = RendererOptions().apply {
        aspectRatioMode = AspectRatioMode.LETTERBOX
    }
    var joinChannelBuilder = ChannelExpressFactory
        .createJoinChannelOptionsBuilder()
        .withStreamToken(channelConfig.edgeToken)
        .withRenderer(surface)
        .withRendererOptions(rendererOptions)

    return joinChannelBuilder.buildJoinChannelOptions()
}
