/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.core

import android.content.Context
import android.graphics.Bitmap
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.RequestStatus
import com.phenixrts.environment.android.AndroidContext
import com.phenixrts.express.*
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.repositories.channel.PhenixChannelRepository
import com.phenixrts.suite.phenixcore.repositories.chat.PhenixChatRepository
import com.phenixrts.suite.phenixcore.repositories.core.models.PhenixCoreState
import com.phenixrts.suite.phenixcore.repositories.models.*
import com.phenixrts.suite.phenixcore.repositories.room.PhenixRoomRepository
import com.phenixrts.suite.phenixcore.repositories.stream.PhenixStreamRepository
import com.phenixrts.suite.phenixcore.repositories.usermedia.UserMediaRepository
import kotlinx.coroutines.flow.*
import timber.log.Timber

internal class PhenixCoreRepository(
    private val context: Context,
    private val debugTree: FileWriterDebugTree
) {

    private var configuration = PhenixConfiguration()
    private var roomExpress: RoomExpress? = null
    private var channelRepository: PhenixChannelRepository? = null
    private var streamRepository: PhenixStreamRepository? = null
    private var roomRepository: PhenixRoomRepository? = null
    private var userMediaRepository: UserMediaRepository? = null
    private var chatRepository: PhenixChatRepository? = null

    private var _phenixState = PhenixCoreState.NOT_INITIALIZED
    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private val _channels = ConsumableSharedFlow<List<PhenixChannel>>(canReplay = true)
    private val _streams = ConsumableSharedFlow<List<PhenixStream>>(canReplay = true)
    private val _messages = ConsumableSharedFlow<List<PhenixMessage>>(canReplay = true)
    private val _members = ConsumableSharedFlow<List<PhenixMember>>(canReplay = true)
    private val _room = ConsumableSharedFlow<PhenixRoom?>(canReplay = true)
    private var _memberCount = ConsumableSharedFlow<Long>(canReplay = true)
    private var _mediaState = ConsumableSharedFlow<PhenixMediaState>(canReplay = true)

    val onError = _onError.asSharedFlow()
    val onEvent = _onEvent.asSharedFlow()
    val channels = _channels.asSharedFlow()
    val streams = _streams.asSharedFlow()
    val members = _members.asSharedFlow()
    val messages = _messages.asSharedFlow()
    val room = _room.asSharedFlow()
    val memberCount = _memberCount.asSharedFlow()
    val mediaState = _mediaState.asSharedFlow()

    val isPhenixInitializing get() = _phenixState == PhenixCoreState.INITIALIZING
    val isPhenixInitialized get() = _phenixState == PhenixCoreState.INITIALIZED

    fun init(phenixConfiguration: PhenixConfiguration?) {
        _phenixState = PhenixCoreState.INITIALIZING
        if (phenixConfiguration?.enableDebugging == true) {
            Timber.plant(debugTree)
        }
        if (phenixConfiguration != null) {
            configuration = phenixConfiguration
        }
        Timber.d("Initializing Phenix Core with configuration: $configuration")
        AndroidContext.setContext(context)
        var pcastBuilder = PCastExpressFactory.createPCastExpressOptionsBuilder()
            .withMinimumConsoleLogLevel(configuration.logLevel.level)
            .withPCastUri(configuration.uri)
            .withUnrecoverableErrorCallback { status: RequestStatus, description: String ->
                Timber.e("Failed to initialize Phenix Core: $status, $description with configuration: $configuration")
                _onError.tryEmit(PhenixError.FAILED_TO_INITIALIZE)
            }
        pcastBuilder = if (!configuration.authToken.isNullOrBlank()) {
            Timber.d("Initializing pcast with auth token: ${configuration.authToken}")
            pcastBuilder.withAuthenticationToken(configuration.authToken)
        } else {
            Timber.d("Missing token, can't initialize pcast")
            _onError.tryEmit(PhenixError.MISSING_TOKEN)
            return
        }
        val roomExpressOptions = RoomExpressFactory.createRoomExpressOptionsBuilder()
            .withPCastExpressOptions(pcastBuilder.buildPCastExpressOptions())
            .buildRoomExpressOptions()

        val channelExpressOptions = ChannelExpressFactory.createChannelExpressOptionsBuilder()
            .withRoomExpressOptions(roomExpressOptions)
            .buildChannelExpressOptions()

        ChannelExpressFactory.createChannelExpress(channelExpressOptions)?.let { express ->
            express.roomExpress?.pCastExpress?.waitForOnline {
                Timber.d("Phenix Core initialized with configuration: $configuration")
                roomExpress = express.roomExpress
                val chatRepository = PhenixChatRepository()
                this.chatRepository = chatRepository
                channelRepository = PhenixChannelRepository(express, configuration, chatRepository)
                streamRepository = PhenixStreamRepository(express.pCastExpress, configuration)
                roomRepository = PhenixRoomRepository(express.roomExpress, configuration, chatRepository)
                enablePublishing(configuration.publishingEnabled)
                _phenixState = PhenixCoreState.INITIALIZED
                _onEvent.tryEmit(PhenixEvent.PHENIX_CORE_INITIALIZED)

                launchIO { channelRepository?.onError?.collect { _onError.tryEmit(it) } }
                launchIO { channelRepository?.onEvent?.collect { _onEvent.tryEmit(it) } }
                launchIO { channelRepository?.channels?.collect { _channels.tryEmit(it) } }

                launchIO { streamRepository?.onError?.collect { _onError.tryEmit(it) } }
                launchIO { streamRepository?.onEvent?.collect { _onEvent.tryEmit(it) } }
                launchIO { streamRepository?.streams?.collect { _streams.tryEmit(it) } }

                launchIO { roomRepository?.onError?.collect { _onError.tryEmit(it) } }
                launchIO { roomRepository?.onEvent?.collect { _onEvent.tryEmit(it) } }
                launchIO { roomRepository?.members?.collect { _members.tryEmit(it) } }
                launchIO { roomRepository?.room?.collect { _room.tryEmit(it) } }
                launchIO { roomRepository?.memberCount?.collect { _memberCount.tryEmit(it) } }

                launchIO { chatRepository.messages.collect { _messages.tryEmit(it) } }
                launchIO { chatRepository.onError.collect { _onError.tryEmit(it) } }
                launchIO { chatRepository.onEvent.collect { _onEvent.tryEmit(it) } }
            }
        } ?: run {
            Timber.e("Failed to initialize Phenix Core")
            _phenixState = PhenixCoreState.NOT_INITIALIZED
            _onError.tryEmit(PhenixError.FAILED_TO_INITIALIZE)
        }
    }

    fun enablePublishing(publishingEnabled: Boolean = false) {
        configuration.publishingEnabled = publishingEnabled
        if (configuration.publishingEnabled) {
            if (roomExpress == null) {
                _onError.tryEmit(PhenixError.PUBLISH_ROOM_FAILED)
                return
            }
            if (userMediaRepository != null) return
            UserMediaRepository(
                roomExpress!!.pCastExpress,
                configuration,
                onMicrophoneFailure = {
                    roomRepository?.setSelfAudioEnabled(false)
                },
                onCameraFailure = {
                    roomRepository?.setSelfVideoEnabled(false)
                }
            ).run {
                userMediaRepository = this
                launchIO { mediaState.collect { _mediaState.tryEmit(it) } }
                launchIO { onError.collect { _onError.tryEmit(it) } }
                launchIO { onEvent.collect { _onEvent.tryEmit(it) } }
                Timber.d("User media initialized")
            }
        } else {
            userMediaRepository?.release()
            userMediaRepository = null
        }
    }

    fun joinAllChannels(channelAliases: List<String>) =
        channelRepository?.joinAllChannels(channelAliases)

    fun joinAllStreams(streamIDs: List<String>) =
        streamRepository?.joinAllStreams(streamIDs)

    fun joinChannel(configuration: PhenixChannelConfiguration) =
        channelRepository?.joinChannel(configuration)

    fun joinStream(configuration: PhenixStreamConfiguration) =
        streamRepository?.joinStream(configuration)

    fun leaveChannel(channelAlias: String) = channelRepository?.leaveChannel(channelAlias)

    fun leaveStream(streamID: String) = streamRepository?.leaveStream(streamID)

    fun leaveAllChannels() = channelRepository?.release()

    fun leaveAllStreams() = streamRepository?.release()

    fun publishToChannel(configuration: PhenixChannelConfiguration, publishConfiguration: PhenixPublishConfiguration) {
        whenPublishingEnabled {
            userMediaRepository?.updateUserMedia(publishConfiguration) { status, userMedia ->
                if (status == RequestStatus.OK) {
                    channelRepository?.publishToChannel(configuration, userMedia)
                } else {
                    _onError.tryEmit(PhenixError.PUBLISH_CHANNEL_FAILED.apply { data = configuration })
                }
            }
        }
    }

    fun stopPublishingToChannel() = channelRepository?.stopPublishingToChannel()

    fun selectChannel(alias: String, isSelected: Boolean) =
        channelRepository?.selectChannel(alias, isSelected)

    fun selectStream(alias: String, isSelected: Boolean) =
        streamRepository?.selectStream(alias, isSelected)

    fun flipCamera() = whenPublishingEnabled {
        userMediaRepository?.flipCamera()
    }

    fun setCameraFacing(facing: PhenixFacingMode) = whenPublishingEnabled {
        userMediaRepository?.setCameraFacing(facing)
    }

    fun renderOnSurface(alias: String, surfaceView: SurfaceView?) {
        channelRepository?.renderOnSurface(alias, surfaceView)
        streamRepository?.renderOnSurface(alias, surfaceView)
        roomRepository?.renderOnSurface(alias, surfaceView)
    }

    fun renderOnImage(alias: String, imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        channelRepository?.renderOnImage(alias, imageView, configuration)
        streamRepository?.renderOnImage(alias, imageView, configuration)
        roomRepository?.renderOnImage(alias, imageView, configuration)
    }

    fun previewOnSurface(surfaceView: SurfaceView?) = whenPublishingEnabled {
        userMediaRepository?.renderOnSurface(surfaceView)
    }

    fun previewOnImage(imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) = whenPublishingEnabled {
        userMediaRepository?.renderOnImage(imageView, configuration)
    }

    fun createTimeShift(alias: String, timestamp: Long) {
        channelRepository?.createTimeShift(alias, timestamp)
        streamRepository?.createTimeShift(alias, timestamp)
    }

    fun seekTimeShift(alias: String, offset: Long) {
        channelRepository?.seekTimeShift(alias, offset)
        streamRepository?.seekTimeShift(alias, offset)
    }

    fun playTimeShift(alias: String) {
        channelRepository?.playTimeShift(alias)
        streamRepository?.playTimeShift(alias)
    }

    fun startTimeShift(alias: String, duration: Long) {
        channelRepository?.startTimeShift(alias, duration)
        streamRepository?.startTimeShift(alias, duration)
    }

    fun pauseTimeShift(alias: String) {
        channelRepository?.pauseTimeShift(alias)
        streamRepository?.pauseTimeShift(alias)
    }

    fun stopTimeShift(alias: String) {
        channelRepository?.stopTimeShift(alias)
        streamRepository?.stopTimeShift(alias)
    }

    fun limitBandwidth(alias: String, bandwidth: Long) =
        channelRepository?.limitBandwidth(alias, bandwidth)

    fun releaseBandwidthLimiter(alias: String) =
        channelRepository?.releaseBandwidthLimiter(alias)

    fun setAudioEnabled(alias: String, enabled: Boolean) {
        channelRepository?.setAudioEnabled(alias, enabled)
        streamRepository?.setAudioEnabled(alias, enabled)
        roomRepository?.setAudioEnabled(alias, enabled)
    }

    fun setSelfAudioEnabled(enabled: Boolean) {
        roomRepository?.setSelfAudioEnabled(enabled)
        whenPublishingEnabled(shouldNotify = false) {
            userMediaRepository?.setSelfAudioEnabled(enabled)
        }
    }

    fun setVideoEnabled(alias: String, enabled: Boolean) =
        roomRepository?.setVideoEnabled(alias, enabled)

    fun setSelfVideoEnabled(enabled: Boolean) {
        roomRepository?.setSelfVideoEnabled(enabled)
        whenPublishingEnabled(shouldNotify = false) {
            userMediaRepository?.setSelfVideoEnabled(enabled)
        }
    }

    fun joinRoom(configuration: PhenixRoomConfiguration) =
        roomRepository?.joinRoom(configuration)

    fun createRoom(configuration: PhenixRoomConfiguration) =
        roomRepository?.createRoom(configuration)

    fun stopPublishingToRoom() = roomRepository?.stopPublishingToRoom()

    fun leaveRoom() = roomRepository?.leaveRoom()

    fun updateMember(memberId: String, role: PhenixMemberRole?, active: PhenixMemberState?, name: String?) =
        roomRepository?.updateMember(memberId, role, active, name)

    fun setAudioLevel(memberId: String, level: Float) =
        roomRepository?.setAudioLevel(memberId, level)

    fun subscribeForMessages(alias: String, configuration: PhenixMessageConfiguration) {
        roomRepository?.subscribeForMessages(alias, configuration)
        channelRepository?.subscribeForMessages(alias, configuration)
    }

    fun sendMessage(alias: String, message: String, mimeType: String) =
        chatRepository?.sendMessage(alias, message, mimeType)

    fun selectMember(memberId: String, isSelected: Boolean) =
        roomRepository?.selectMember(memberId, isSelected)

    fun publishToRoom(configuration: PhenixRoomConfiguration, publishConfiguration: PhenixPublishConfiguration) {
        whenPublishingEnabled {
            if (roomRepository?.isRoomJoined == false) {
                _onError.tryEmit(PhenixError.JOIN_BEFORE_PUBLISHING.apply { data = configuration })
                return@whenPublishingEnabled
            }
            userMediaRepository?.updateUserMedia(publishConfiguration) { status, userMedia ->
                if (status == RequestStatus.OK) {
                    roomRepository?.publishToRoom(configuration, userMedia)
                } else {
                    _onError.tryEmit(PhenixError.PUBLISH_ROOM_FAILED.apply { data = configuration })
                }
            }
        }
    }

    fun observeVideoFrames(onFrame: (Bitmap) -> Bitmap) {
        whenPublishingEnabled {
            userMediaRepository?.observeVideoFrames(onFrame)
        }
    }

    fun subscribeToRoom() = roomRepository?.subscribeRoomMembers()

    fun collectLogs(onCollected: (String) -> Unit) {
        roomExpress?.pCastExpress?.pCast?.collectLogMessages { _, _, messages ->
            Timber.d("SDK logs collected")
            onCollected(messages)
        }
    }

    fun release() {
        roomExpress?.dispose()
        channelRepository?.release()
        streamRepository?.release()
        roomRepository?.release()
        userMediaRepository?.release()
        chatRepository?.release()

        configuration = PhenixConfiguration()
        roomExpress = null
        channelRepository = null
        streamRepository = null
        roomRepository = null
        userMediaRepository = null
        chatRepository = null
        _phenixState = PhenixCoreState.NOT_INITIALIZED
    }

    private fun whenPublishingEnabled(shouldNotify: Boolean = true, whenEnabled: () -> Unit) {
        if (userMediaRepository == null) {
            Timber.d("User media not initialized")
            if (shouldNotify) {
                _onError.tryEmit(PhenixError.PUBLISHING_NOT_ENABLED)
            }
            return
        }
        whenEnabled()
    }

}
