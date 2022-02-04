/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui.viewmodel

import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.phenixclosedcaptions.PhenixClosedCaptionView
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelConfiguration
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelState
import com.phenixrts.suite.phenixdebugmenu.DebugMenu
import com.phenixrts.suite.phenixdebugmenu.models.DebugEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class ChannelViewModel(private val phenixCore: PhenixCore) : ViewModel() {

    private val _onChannelState = MutableSharedFlow<PhenixChannelState>(replay = 1)
    private var joinedChannel = ""

    val onChannelState: SharedFlow<PhenixChannelState> = _onChannelState

    init {
        launchMain {
            phenixCore.channels.collect { channels ->
                channels.firstOrNull()?.let { channel ->
                    _onChannelState.tryEmit(channel.channelState)
                }
            }
        }
    }

    fun joinChannel(channelAlias: String) {
        joinedChannel = channelAlias
        phenixCore.joinChannel(PhenixChannelConfiguration(channelAlias))
    }

    fun updateSurface(surface: SurfaceView) {
        phenixCore.renderOnSurface(joinedChannel, surface)
    }

    fun subscribeToClosedCaptions(closedCaptionView: PhenixClosedCaptionView) {
        closedCaptionView.subscribeToCC(phenixCore, joinedChannel)
    }

    fun observeDebugMenu(debugMenu: DebugMenu, onError: (String) -> Unit, onEvent: (DebugEvent) -> Unit) {
        debugMenu.observeDebugMenu(
            phenixCore,
            "${BuildConfig.APPLICATION_ID}.provider",
            onError = onError,
            onEvent = onEvent
        )
    }
}
