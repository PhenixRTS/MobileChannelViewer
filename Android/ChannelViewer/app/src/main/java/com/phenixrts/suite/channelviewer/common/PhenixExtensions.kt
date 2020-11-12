/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.common

import androidx.lifecycle.MutableLiveData
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.ChannelExpress
import com.phenixrts.express.ExpressSubscriber
import com.phenixrts.express.JoinChannelOptions
import com.phenixrts.express.PCastExpress
import com.phenixrts.pcast.Renderer
import com.phenixrts.room.RoomService
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.phenixcommon.common.launchMain
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun PCastExpress.waitForOnline() = suspendCoroutine<Unit> { continuation ->
    waitForOnline {
        continuation.resume(Unit)
    }
}

fun ChannelExpress.joinChannel(options: JoinChannelOptions): MutableLiveData<ChannelJoinedState> {
    val status = MutableLiveData<ChannelJoinedState>()
    joinChannel(options, { requestStatus: RequestStatus?, roomService: RoomService? ->
        launchMain {
            Timber.d("Channel status: $requestStatus")
            if (requestStatus == RequestStatus.OK) {
                status.value = ChannelJoinedState(ConnectionStatus.CONNECTED, roomService)
            } else {
                status.value = ChannelJoinedState(ConnectionStatus.FAILED)
            }
        }
    }, { requestStatus: RequestStatus?, _: ExpressSubscriber?, _: Renderer? ->
        launchMain{
            Timber.d("Stream status: $requestStatus")
            when (requestStatus) {
                RequestStatus.OK -> status.value = ChannelJoinedState(ConnectionStatus.ONLINE)
                RequestStatus.NO_STREAM_PLAYING -> status.value = ChannelJoinedState(ConnectionStatus.OFFLINE)
                else -> status.value = ChannelJoinedState(ConnectionStatus.FAILED)
            }
        }
    })
    return status
}

data class ChannelJoinedState(val connectionStatus: ConnectionStatus, val roomService: RoomService? = null)
