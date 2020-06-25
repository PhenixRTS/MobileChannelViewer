/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.repositories

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.phenixrts.common.RequestStatus
import com.phenixrts.environment.android.AndroidContext
import com.phenixrts.express.*
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.suite.channelviewer.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val REINITIALIZATION_DELAY = 1000L

class ChannelExpressRepository(private val context: Application) {

    private var channelExpress: ChannelExpress? = null
    private var currentConfiguration = ChannelConfiguration()
    val onChannelExpressError = MutableLiveData<Unit>()
    val onChannelState = MutableLiveData<ChannelJoinedState>()
    var roomExpress: RoomExpress? = null

    private fun initializeChannelExpress() {
        Timber.d("Creating Channel Express: $currentConfiguration")
        AndroidContext.setContext(context)
        val pcastExpressOptions = PCastExpressFactory.createPCastExpressOptionsBuilder()
            .withBackendUri(currentConfiguration.backend)
            .withPCastUri(currentConfiguration.uri)
            .withUnrecoverableErrorCallback { status: RequestStatus?, description: String ->
                Timber.e("Unrecoverable error in PhenixSDK. Error status: [$status]. Description: [$description]")
                onChannelExpressError.value = Unit
            }
            .buildPCastExpressOptions()

        val roomExpressOptions = RoomExpressFactory.createRoomExpressOptionsBuilder()
            .withPCastExpressOptions(pcastExpressOptions)
            .buildRoomExpressOptions()

        val channelExpressOptions = ChannelExpressFactory.createChannelExpressOptionsBuilder()
            .withRoomExpressOptions(roomExpressOptions)
            .buildChannelExpressOptions()

        channelExpress = ChannelExpressFactory.createChannelExpress(channelExpressOptions)
        roomExpress = channelExpress?.roomExpress
    }

    suspend fun setupChannelExpress(configuration: ChannelConfiguration) {
        if (hasConfigurationChanged(configuration)) {
            Timber.d("Channel Express configuration has changed: $configuration")
            currentConfiguration = configuration
            channelExpress?.dispose()
            channelExpress = null
            Timber.d("Channel Express disposed")
            delay(REINITIALIZATION_DELAY)
            initializeChannelExpress()
        }
    }

    suspend fun waitForPCast(): Unit = suspendCoroutine {
        launchMain {
            Timber.d("Waiting for pCast")
            if (channelExpress == null) {
                initializeChannelExpress()
            }
            channelExpress?.pCastExpress?.waitForOnline()
            it.resume(Unit)
        }
    }

    suspend fun joinChannel(channelAlias: String, surface: AndroidVideoRenderSurface) {
        channelExpress?.joinChannel(getChannelConfiguration(channelAlias, surface))?.asFlow()?.collect { status ->
            launchMain {
                onChannelState.value = status
            }
        } ?: launchMain {
            onChannelExpressError.value = Unit
        }
    }

    fun hasConfigurationChanged(configuration: ChannelConfiguration): Boolean = currentConfiguration != configuration
}
