/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.framereadyrotation.ui.viewmodel

import android.view.SurfaceView
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import com.phenixrts.suite.framereadyrotation.BuildConfig
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.closedcaptions.PhenixClosedCaptionView
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.debugmenu.DebugMenu
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelConfiguration
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelState
import com.phenixrts.suite.phenixcore.repositories.models.PhenixFrameReadyConfiguration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import timber.log.Timber

class ChannelViewModel(private val phenixCore: PhenixCore) : ViewModel() {

    private val _onChannelState = MutableSharedFlow<PhenixChannelState>(replay = 1)
    private var joinedChannel = ""
    private var configuration: PhenixFrameReadyConfiguration? = null

    val onChannelState = _onChannelState.asSharedFlow()

    init {
        launchMain {
            phenixCore.channels.collect { channels ->
                channels.firstOrNull()?.let { channel ->
                    if (_onChannelState.replayCache.lastOrNull() != channel.channelState) {
                        _onChannelState.tryEmit(channel.channelState)
                    }
                }
            }
        }
    }

    fun joinChannel(channelAlias: String) {
        joinedChannel = channelAlias
        phenixCore.joinChannel(PhenixChannelConfiguration(channelAlias))
    }

    fun renderOnImage(surface: ImageView) {
        phenixCore.selectChannel(joinedChannel, true)
        phenixCore.renderOnImage(joinedChannel, surface, configuration)
    }

    fun renderOnSurface(surface: SurfaceView) {
        phenixCore.renderOnSurface(joinedChannel, surface)
    }

    fun subscribeToClosedCaptions(closedCaptionView: PhenixClosedCaptionView) {
        phenixCore.subscribeToCC(joinedChannel, closedCaptionView)
    }

    fun observeDebugMenu(debugMenu: DebugMenu) {
        phenixCore.observeDebugMenu(debugMenu, "${BuildConfig.APPLICATION_ID}.provider")
    }

    fun applyChanges(surface: ImageView, rotation: Float, width: Int, height: Int) {
        configuration = PhenixFrameReadyConfiguration(rotation, width, height)
        Timber.d("Applying changes: $configuration")
        renderOnImage(surface)
    }
}
