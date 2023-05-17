/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui.viewmodel

import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.phenixcommon.common.launchMain
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ChannelViewModel(private val channelExpressRepository: ChannelExpressRepository) : ViewModel() {

    private val androidVideoSurface = AndroidVideoRenderSurface()

    val onChannelExpressError = channelExpressRepository.onChannelExpressError
    val onAuthenticationStatus = channelExpressRepository.onAuthenticationStatus
    val mimeTypes = channelExpressRepository.mimeTypes
    val onChannelStreamPlaying = channelExpressRepository.onChannelStreamPlaying
    val onVideoDisplayOptionsChanged = channelExpressRepository.onVideoDisplayOptionsChanged

    suspend fun joinChannel(): ConnectionStatus = suspendCoroutine { continuation ->
        launchMain {
            continuation.resume(channelExpressRepository.joinChannel(androidVideoSurface))
        }
    }

    fun updateSurfaceHolder(holder: SurfaceHolder) {
        androidVideoSurface.setSurfaceHolder(holder)
    }

    fun updateAuthenticationToken(authenticationToken: String) {
        channelExpressRepository.updateAuthenticationToken(authenticationToken);
    }
}
