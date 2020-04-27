/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui.viewmodel

import android.view.SurfaceHolder
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.RoomService
import com.phenixrts.suite.channelviewer.common.enums.StreamStatus
import com.phenixrts.suite.channelviewer.common.launchMain
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import kotlinx.coroutines.flow.collect
import timber.log.Timber

class ChannelViewModel(private val channelExpressRepository: ChannelExpressRepository) : ViewModel() {

    private val androidVideoSurface = AndroidVideoRenderSurface()
    private var roomService: RoomService? = null
    val onChannelExpressError = channelExpressRepository.onChannelExpressError
    val onChannelState = MutableLiveData<StreamStatus>()

    init {
        launchMain {
            channelExpressRepository.onChannelState.asFlow().collect { state ->
                if (state.streamStatus == StreamStatus.CONNECTED) {
                    roomService = state.roomService
                }
                onChannelState.value = state.streamStatus
            }
        }
    }

    fun joinChannel(channelCode: String, surfaceHolder: SurfaceHolder) = launchMain {
        Timber.d("Joining channel: $channelCode")
        updateSurfaceHolder(surfaceHolder)
        channelExpressRepository.joinChannel(channelCode, androidVideoSurface)
    }

    fun updateSurfaceHolder(holder: SurfaceHolder) {
        androidVideoSurface.setSurfaceHolder(holder)
    }
}
