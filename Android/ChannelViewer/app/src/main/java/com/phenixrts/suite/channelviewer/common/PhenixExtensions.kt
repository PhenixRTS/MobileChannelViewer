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
import com.phenixrts.suite.channelviewer.common.enums.StreamStatus
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun launchMain(block: suspend CoroutineScope.() -> Unit) = mainScope.launch(
    context = CoroutineExceptionHandler { _, e ->
        Timber.w(e, "Coroutine failed: ${e.localizedMessage}")
    },
    block = block
)

fun launchIO(block: suspend CoroutineScope.() -> Unit) = ioScope.launch(
    context = CoroutineExceptionHandler { _, e ->
        Timber.w(e, "Coroutine failed: ${e.localizedMessage}")
    },
    block = block
)

suspend fun PCastExpress.waitForOnline() = suspendCoroutine<Unit> { continuation ->
    waitForOnline {
        continuation.resume(Unit)
    }
}

fun ChannelExpress.joinChannel(options: JoinChannelOptions): MutableLiveData<ChannelJoinedState> {
    val status = MutableLiveData<ChannelJoinedState>()
    joinChannel(options, { requestStatus: RequestStatus?, roomService: RoomService? ->
        launchMain {
            if (requestStatus == RequestStatus.OK) {
                status.value = ChannelJoinedState(StreamStatus.CONNECTED, roomService)
            } else {
                status.value = ChannelJoinedState(StreamStatus.FAILED)
            }
        }
    }, { requestStatus: RequestStatus?, _: ExpressSubscriber?, _: Renderer? ->
        launchMain{
            Timber.d("Stream status: $requestStatus")
            when (requestStatus) {
                RequestStatus.OK -> status.value = ChannelJoinedState(StreamStatus.ONLINE)
                RequestStatus.NO_STREAM_PLAYING -> status.value = ChannelJoinedState(StreamStatus.OFFLINE)
                else -> status.value = ChannelJoinedState(StreamStatus.FAILED)
            }
        }
    })
    return status
}

data class ChannelJoinedState(val streamStatus: StreamStatus, val roomService: RoomService? = null)
