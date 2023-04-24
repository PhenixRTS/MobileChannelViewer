/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.repositories

import android.app.Application
import com.phenixrts.common.AuthenticationStatus
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.environment.android.AndroidContext
import com.phenixrts.express.ChannelExpress
import com.phenixrts.express.ChannelExpressFactory
import com.phenixrts.express.ExpressSubscriber
import com.phenixrts.express.PCastExpressFactory
import com.phenixrts.express.RoomExpress
import com.phenixrts.express.RoomExpressFactory
import com.phenixrts.pcast.AspectRatioMode
import com.phenixrts.pcast.Renderer
import com.phenixrts.pcast.RendererOptions
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.RoomService
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.common.collectLogs
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.channelviewer.common.enums.ExpressError
import com.phenixrts.suite.channelviewer.common.waitForOnline
import com.phenixrts.suite.phenixcommon.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcommon.common.launchMain
import com.phenixrts.suite.phenixdeeplink.common.ChannelConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

private const val REINITIALIZATION_DELAY = 1000L

class ChannelExpressRepository(private val context: Application) {

    @Inject
    lateinit var fileWriterTree: FileWriterDebugTree

    private var expressConfiguration = ChannelConfiguration()
    private var channelExpress: ChannelExpress? = null
    private var authenticationStatusChangeSubscription : Disposable? = null

    var roomExpress: RoomExpress? = null
    val onChannelExpressError = ConsumableSharedFlow<ExpressError>()
    val onChannelState = ConsumableSharedFlow<ChannelJoinedState>()
    val onAuthenticationStatus = ConsumableSharedFlow<AuthenticationStatus>()
    val mimeTypes = ConsumableSharedFlow<List<String>>()
    var roomService: RoomService? = null

    init {
        ChannelViewerApplication.component.inject(this)
    }

    private fun hasConfigurationChanged(configuration: ChannelConfiguration): Boolean = expressConfiguration != configuration

    private fun initializeChannelExpress() {
        Timber.d("Creating Channel Express: $expressConfiguration")
        AndroidContext.setContext(context)
        var pcastBuilder = PCastExpressFactory.createPCastExpressOptionsBuilder { status: RequestStatus?, description: String ->
                launchMain {
                    Timber.e("Unrecoverable error in PhenixSDK. Error status: [$status]. Description: [$description]")
                    onChannelExpressError.tryEmit(ExpressError.UNRECOVERABLE_ERROR)
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
                    onAuthenticationStatus.tryEmit(status)
                }
            }

            roomExpress?.pCastExpress?.pCast?.run {
                fileWriterTree.setLogCollectionMethod(this::collectLogs)
            }

            Timber.d("Channel express initialized")
        } ?: run {
            Timber.e("Unrecoverable error in PhenixSDK")
            onChannelExpressError.tryEmit(ExpressError.UNRECOVERABLE_ERROR)
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
            Timber.d("Joining channel")

            val rendererOptions = RendererOptions().apply {
                aspectRatioMode = AspectRatioMode.LETTERBOX
            }
            var joinChannelOptions = ChannelExpressFactory
                .createJoinChannelOptionsBuilder()
                .withRenderer(surface)
                .withRendererOptions(rendererOptions)
                .withStreamToken(expressConfiguration.edgeToken)
                .buildJoinChannelOptions()

            channelExpress?.joinChannel(joinChannelOptions,
                // Channel joined callback
                { requestStatus: RequestStatus?, service: RoomService? ->
                    Timber.d("Channel joined with status [$requestStatus]")
                    if (requestStatus == RequestStatus.OK) {
                        mimeTypes.tryEmit(expressConfiguration.mimeTypes)
                        roomService = service
                        if (continuation.isActive) {
                            continuation.resume(ConnectionStatus.CONNECTED)
                        }
                    } else {
                        onChannelExpressError.tryEmit(ExpressError.UNRECOVERABLE_ERROR)
                        if (continuation.isActive) {
                            continuation.resume(ConnectionStatus.FAILED)
                        }
                    }
                },
                // Channel subscribed callback
                { requestStatus: RequestStatus?, _: ExpressSubscriber?, _: Renderer? ->
                    launchMain{
                        Timber.d("Channel subscribed with status [$requestStatus]")
                        when (requestStatus) {
                            RequestStatus.OK -> onChannelState.tryEmit(ChannelJoinedState(ConnectionStatus.ONLINE, roomService))
                            RequestStatus.NO_STREAM_PLAYING -> onChannelState.tryEmit(ChannelJoinedState(ConnectionStatus.OFFLINE))
                            else -> onChannelState.tryEmit(ChannelJoinedState(ConnectionStatus.FAILED))
                        }
                    }
            })
        }
    }

    fun updateAuthenticationToken(authenticationToken: String) {
        channelExpress?.pCastExpress?.setAuthenticationToken(authenticationToken)
    }

    fun isRoomExpressInitialized(): Boolean = roomExpress != null
}

data class ChannelJoinedState(val connectionStatus: ConnectionStatus, val roomService: RoomService? = null)
