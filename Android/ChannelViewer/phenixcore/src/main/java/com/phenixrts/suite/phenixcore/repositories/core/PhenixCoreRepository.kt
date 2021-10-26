/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.core

import android.content.Context
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.RequestStatus
import com.phenixrts.environment.android.AndroidContext
import com.phenixrts.express.*
import com.phenixrts.room.MemberRole
import com.phenixrts.room.MemberState
import com.phenixrts.suite.phenixcore.BuildConfig
import com.phenixrts.suite.phenixcore.closedcaptions.PhenixClosedCaptionView
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.debugmenu.DebugMenu
import com.phenixrts.suite.phenixcore.debugmenu.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcore.repositories.channels.PhenixChannelRepository
import com.phenixrts.suite.phenixcore.repositories.core.common.PHENIX_LOG_LEVEL
import com.phenixrts.suite.phenixcore.repositories.core.models.PhenixCoreState
import com.phenixrts.suite.phenixcore.repositories.models.*
import com.phenixrts.suite.phenixcore.repositories.room.PhenixRoomRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import timber.log.Timber

internal class PhenixCoreRepository(
    private val context: Context,
    private val debugTree: FileWriterDebugTree
) {

    private var configuration = PhenixConfiguration()
    private var roomExpress: RoomExpress? = null
    private var channelRepository: PhenixChannelRepository? = null
    private var roomRepository: PhenixRoomRepository? = null

    private var _phenixState = PhenixCoreState.NOT_INITIALIZED
    private val _onError = MutableSharedFlow<PhenixError>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _onEvent = MutableSharedFlow<PhenixEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _channels = MutableSharedFlow<List<PhenixChannel>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _messages = MutableSharedFlow<List<PhenixMessage>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _members = MutableSharedFlow<List<PhenixMember>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var _memberCount = MutableSharedFlow<Long>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val onError: SharedFlow<PhenixError> = _onError
    val onEvent: SharedFlow<PhenixEvent> = _onEvent
    val channels: SharedFlow<List<PhenixChannel>> = _channels
    val members: SharedFlow<List<PhenixMember>> = _members
    val messages: SharedFlow<List<PhenixMessage>> = _messages
    val memberCount: SharedFlow<Long> = _memberCount

    val isPhenixInitializing get() = _phenixState == PhenixCoreState.INITIALIZING
    val isPhenixInitialized get() = _phenixState == PhenixCoreState.INITIALIZED

    fun init(config: PhenixConfiguration?) {
        _phenixState = PhenixCoreState.INITIALIZING
        AndroidContext.setContext(context)
        if (BuildConfig.DEBUG) {
            Timber.plant(debugTree)
        }
        if (config != null) {
            configuration = config
        }
        Timber.d("Initializing Phenix Core with configuration: $configuration")
        AndroidContext.setContext(context)
        var pcastBuilder = PCastExpressFactory.createPCastExpressOptionsBuilder()
            .withMinimumConsoleLogLevel(PHENIX_LOG_LEVEL)
            .withPCastUri(configuration.uri)
            .withUnrecoverableErrorCallback { status: RequestStatus, description: String ->
                Timber.e("Failed to initialize Phenix Core: $status, $description with configuration: $configuration")
                _onError.tryEmit(PhenixError.FAILED_TO_INITIALIZE)
            }
        pcastBuilder = if (configuration.authToken?.isNotBlank() == true) {
            pcastBuilder.withAuthenticationToken(configuration.authToken)
        } else {
            pcastBuilder.withBackendUri(configuration.backend)
        }
        val roomExpressOptions = RoomExpressFactory.createRoomExpressOptionsBuilder()
            .withPCastExpressOptions(pcastBuilder.buildPCastExpressOptions())
            .buildRoomExpressOptions()

        val channelExpressOptions = ChannelExpressFactory.createChannelExpressOptionsBuilder()
            .withRoomExpressOptions(roomExpressOptions)
            .buildChannelExpressOptions()

        ChannelExpressFactory.createChannelExpress(channelExpressOptions)?.let { express ->
            express.roomExpress?.pCastExpress?.waitForOnline {
                Timber.d("Phenix Core initialized")
                roomExpress = express.roomExpress
                channelRepository = PhenixChannelRepository(express, configuration)
                roomRepository = PhenixRoomRepository(express.roomExpress)
                _phenixState = PhenixCoreState.INITIALIZED
                _onEvent.tryEmit(PhenixEvent.PHENIX_CORE_INITIALIZED)

                launchIO { channelRepository?.onError?.collect { _onError.tryEmit(it) } }
                launchIO { channelRepository?.onEvent?.collect { _onEvent.tryEmit(it) } }
                launchIO { channelRepository?.channels?.collect { _channels.tryEmit(it) } }

                launchIO { roomRepository?.onError?.collect { _onError.tryEmit(it) } }
                launchIO { roomRepository?.onEvent?.collect { _onEvent.tryEmit(it) } }
                launchIO { roomRepository?.members?.collect { _members.tryEmit(it) } }
                launchIO { roomRepository?.messages?.collect { _messages.tryEmit(it) } }
                launchIO { roomRepository?.memberCount?.collect { _memberCount.tryEmit(it) } }
            }
        } ?: run {
            Timber.e("Failed to initialize Phenix Core")
            _phenixState = PhenixCoreState.NOT_INITIALIZED
            _onError.tryEmit(PhenixError.FAILED_TO_INITIALIZE)
        }
    }

    fun joinAllChannels(aliases: List<String>) =
        channelRepository?.joinAllChannels(aliases)

    fun joinChannel(config: PhenixChannelConfiguration) =
        channelRepository?.joinChannel(config)

    fun selectChannel(alias: String, isSelected: Boolean) =
        channelRepository?.selectChannel(alias, isSelected)

    fun renderOnSurface(alias: String, surfaceView: SurfaceView?) {
        channelRepository?.renderOnSurface(alias, surfaceView)
        roomRepository?.renderOnSurface(alias, surfaceView)
    }

    fun renderOnImage(alias: String, imageView: ImageView?) =
        channelRepository?.renderOnImage(alias, imageView)

    fun createTimeShift(alias: String, timestamp: Long) =
        channelRepository?.createTimeShift(alias, timestamp)

    fun seekTimeShift(alias: String, offset: Long) =
        channelRepository?.seekTimeShift(alias, offset)

    fun playTimeShift(alias: String) =
        channelRepository?.playTimeShift(alias)

    fun startTimeShift(alias: String, duration: Long) =
        channelRepository?.startTimeShift(alias, duration)

    fun pauseTimeShift(alias: String) =
        channelRepository?.pauseTimeShift(alias)

    fun stopTimeShift(alias: String) =
        channelRepository?.stopTimeShift(alias)

    fun limitBandwidth(alias: String, bandwidth: Long) =
        channelRepository?.limitBandwidth(alias, bandwidth)

    fun releaseBandwidthLimiter(alias: String) =
        channelRepository?.releaseBandwidthLimiter(alias)

    fun subscribeToCC(alias: String, closedCaptionView: PhenixClosedCaptionView) =
        channelRepository?.subscribeToCC(alias, closedCaptionView)

    fun setAudioEnabled(alias: String, enabled: Boolean) {
        channelRepository?.setAudioEnabled(alias, enabled)
        roomRepository?.setAudioEnabled(alias, enabled)
    }

    fun setVideoEnabled(alias: String, enabled: Boolean) =
        roomRepository?.setVideoEnabled(alias, enabled)

    fun joinRoom(configuration: PhenixRoomConfiguration) =
        roomRepository?.joinRoom(configuration)

    fun stopPublishing() = roomRepository?.stopPublishing()

    fun leaveRoom() = roomRepository?.leaveRoom()

    fun updateMember(memberId: String, role: MemberRole?, active: MemberState?, name: String?) =
        roomRepository?.updateMember(memberId, role, active, name)

    fun setAudioLevel(memberId: String, level: Float) =
        roomRepository?.setAudioLevel(memberId, level)

    fun sendMessage(message: String, mimeType: String) =
        roomRepository?.sendMessage(message, mimeType)

    fun selectMember(memberId: String, isSelected: Boolean) =
        roomRepository?.selectMember(memberId, isSelected)

    fun publishToRoom(alias: String, role: MemberRole, name: String?) =
        roomRepository?.publishToRoom(alias, role, name)

    fun subscribeToRoom() = roomRepository?.subscribeRoomMembers()

    fun observeDebugMenu(debugMenu: DebugMenu, authority: String) {
        debugMenu.observeDebugMenu(
            debugTree = debugTree,
            express = roomExpress,
            authority = authority,
            onShow = { _onEvent.tryEmit(PhenixEvent.SHOW_DEBUG_MENU_APP_CHOOSER) },
            onError = { _onError.tryEmit(PhenixError.FAILED_TO_COLLECT_LOGS) }
        )
    }

}
