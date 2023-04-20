/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui.viewmodel

import android.view.SurfaceHolder
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.RoomService
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.phenixcommon.common.launchMain
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ChannelViewModel(private val channelExpressRepository: ChannelExpressRepository) : ViewModel() {

    private val androidVideoSurface = AndroidVideoRenderSurface()
    private var roomService: RoomService? = null
    val onChannelExpressError = channelExpressRepository.onChannelExpressError
    val onAuthenticationStatus = channelExpressRepository.onAuthenticationStatus
    val mimeTypes = channelExpressRepository.mimeTypes
    val onChannelState = MutableLiveData<ConnectionStatus>()

    init {
        launchMain {
            channelExpressRepository.onChannelState.asFlow().collect { state ->
                if (state.connectionStatus == ConnectionStatus.CONNECTED) {
                    roomService = state.roomService
                }
                onChannelState.value = state.connectionStatus
            }
        }
    }

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
