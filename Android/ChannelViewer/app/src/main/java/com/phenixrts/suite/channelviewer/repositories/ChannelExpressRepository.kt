/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.repositories

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.phenixrts.common.AuthenticationStatus
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.environment.android.AndroidContext
import com.phenixrts.express.*
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.RoomService
import com.phenixrts.suite.channelviewer.common.*
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.channelviewer.common.enums.ExpressError
import com.phenixrts.suite.phenixcommon.common.launchMain
import com.phenixrts.suite.phenixdeeplink.common.ChannelConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

private const val REINITIALIZATION_DELAY = 1000L

class ChannelExpressRepository(private val context: Application) {

    private var expressConfiguration = ChannelConfiguration()
    private var channelExpress: ChannelExpress? = null
    private var authenticationStatusChangeSubscription : Disposable? = null

    var roomExpress: RoomExpress? = null

    val onChannelExpressError = MutableLiveData<ExpressError>()
    val onChannelState = MutableLiveData<ChannelJoinedState>()
    val onAuthenticationStatus = MutableLiveData<AuthenticationStatus>()
    val mimeTypes = MutableLiveData<List<String>>()
    var roomService: RoomService? = null

    private fun hasConfigurationChanged(configuration: ChannelConfiguration): Boolean = expressConfiguration != configuration

    private fun initializeChannelExpress() {
        Timber.d("Creating Channel Express: $expressConfiguration")
        AndroidContext.setContext(context)
        var pcastBuilder = PCastExpressFactory.createPCastExpressOptionsBuilder { status: RequestStatus?, description: String ->
            launchMain {
                Timber.e("Unrecoverable error in PhenixSDK. Error status: [$status]. Description: [$description]")
                onChannelExpressError.value = ExpressError.UNRECOVERABLE_ERROR
            }
        }
            .withMinimumConsoleLogLevel("debug")
            .withAuthenticationToken(expressConfiguration.authToken)

        val roomExpressOptions = RoomExpressFactory.createRoomExpressOptionsBuilder()
            .withPCastExpressOptions(pcastBuilder.buildPCastExpressOptions())
            .buildRoomExpressOptions()

        val channelExpressOptions = ChannelExpressFactory.createChannelExpressOptionsBuilder()
            .withRoomExpressOptions(roomExpressOptions)
            .buildChannelExpressOptions()

        ChannelExpressFactory.createChannelExpress(channelExpressOptions)?.let { express ->
            channelExpress = express
            roomExpress = express.roomExpress

            authenticationStatusChangeSubscription = express.pCastExpress.observableAuthenticationStatus.subscribe { status ->
                launchMain {
                    onAuthenticationStatus.value = status
                }
            }

            Timber.d("Channel express initialized")
        } ?: run {
            Timber.e("Unrecoverable error in PhenixSDK")
            onChannelExpressError.value = ExpressError.UNRECOVERABLE_ERROR
        }
    }

    suspend fun setupChannelExpress(configuration: ChannelConfiguration) {
        if (hasConfigurationChanged(configuration)) {
            Timber.d("Channel Express configuration has changed: $configuration")
            expressConfiguration = configuration
            channelExpress?.run {
                dispose()
                Timber.d("Channel Express disposed")
            }
            channelExpress = null
            delay(REINITIALIZATION_DELAY)
            initializeChannelExpress()
        }
    }

    suspend fun waitForPCast() {
        Timber.d("Waiting for pCast")
        if (channelExpress == null) {
            initializeChannelExpress()
        }
        channelExpress?.pCastExpress?.waitForOnline()
    }

    suspend fun joinChannel(surface: AndroidVideoRenderSurface): ConnectionStatus = suspendCancellableCoroutine { continuation ->
        launchMain {
            Timber.d("Joining room")
            channelExpress?.let { express ->
                express.joinChannel(getChannelConfiguration(surface, expressConfiguration)).asFlow().collect { status ->
                    launchMain {
                        Timber.d("Channel status: $status")
                        onChannelState.value = status
                        if (status.connectionStatus == ConnectionStatus.CONNECTED) {
                            status.roomService?.let { service ->
                                mimeTypes.value = expressConfiguration.mimeTypes
                                roomService = service
                                if (continuation.isActive) continuation.resume(ConnectionStatus.CONNECTED)
                            } ?: if (continuation.isActive) continuation.resume(ConnectionStatus.FAILED)
                        } else if (status.connectionStatus == ConnectionStatus.FAILED) {
                            onChannelExpressError.value = ExpressError.UNRECOVERABLE_ERROR
                            if (continuation.isActive) continuation.resume(ConnectionStatus.FAILED)
                        }
                    }
                }
            } ?: launchMain {
                onChannelExpressError.value = ExpressError.UNRECOVERABLE_ERROR
                if (continuation.isActive) continuation.resume(ConnectionStatus.FAILED)
            }
        }
    }

    fun updateAuthenticationToken(authenticationToken: String) {
        channelExpress?.pCastExpress?.setAuthenticationToken(authenticationToken)
    }

    fun isRoomExpressInitialized(): Boolean = roomExpress != null
}
