/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.phenixrts.suite.phenixcore

import android.app.Application
import android.graphics.Bitmap
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.pcast.Renderer
import com.phenixrts.pcast.TimeShift
import com.phenixrts.sdk.BuildConfig
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.injection.DaggerInjectionComponent
import com.phenixrts.suite.phenixcore.injection.InjectionComponent
import com.phenixrts.suite.phenixcore.injection.InjectionModule
import com.phenixrts.suite.phenixcore.repositories.core.PhenixCoreRepository
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

/**
 * The public facade that covers the PhenixSDK behind the scenes.
 *
 * Use the `gradle dokkaHtml` to generate the [PhenixCore] documentation.
 * The generated html document will be located under `build/dokka`.
 */
class PhenixCore(application: Application) {

    @Inject
    internal lateinit var repository: PhenixCoreRepository
    @Inject
    lateinit var debugTree: FileWriterDebugTree

    internal companion object {
        lateinit var component: InjectionComponent
            private set
    }

    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private val _channels = ConsumableSharedFlow<List<PhenixChannel>>(canReplay = true)
    private val _streams = ConsumableSharedFlow<List<PhenixStream>>(canReplay = true)
    private val _room = ConsumableSharedFlow<PhenixRoom?>(canReplay = true)
    private val _messages = ConsumableSharedFlow<List<PhenixMessage>>(canReplay = true)
    private val _logMessages = ConsumableSharedFlow<String>(canReplay = true)
    private val _members = ConsumableSharedFlow<List<PhenixMember>>(canReplay = true)
    private var _memberCount = ConsumableSharedFlow<Long>(canReplay = true)
    private var _mediaState = ConsumableSharedFlow<PhenixMediaState>(canReplay = true)

    /**
     * Emits the latest received [PhenixError].
     * This flow is updated every time any error happens inside the [PhenixCore].
     */
    val onError = _onError.asSharedFlow()

    /**
     * Emits the latest received [PhenixEvent].
     * This flow is updated every time any significant event happens inside the [PhenixCore].
     */
    val onEvent = _onEvent.asSharedFlow()

    /**
     * Holds the list of all channels and their states. This is a super-hot flow and
     * is updated whenever something changes in any given channel. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val channels = _channels.asSharedFlow()

    /**
     * Holds the list of all streams and their states. This is a super-hot flow and
     * is updated whenever something changes in any given channel. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val streams = _streams.asSharedFlow()

    /**
     * Holds the currently connected room and its state or null. This is a super-hot flow and
     * is updated whenever something changes in the connected room.
     * Ideally bind this directly to your UI layer and let the values auto-update the views.
     */
    val rooms = _room.asSharedFlow()

    /**
     * Holds the list of all messages and their states. This is a super-hot flow and
     * is updated whenever something changes in the message list. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val messages = _messages.asSharedFlow()

    /**
     * Holds the list of all members and their states. This is a super-hot flow and
     * is updated whenever something changes for any given member. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val members = _members.asSharedFlow()

    /**
     * Holds the count of actual member instances in the connected room.
     */
    val memberCount = _memberCount.asSharedFlow()

    /**
     * Holds the current user media state
     */
    val mediaState = _mediaState.asSharedFlow()

    /**
     * The configuration that was used when initializing the PhenixCore.
     */
    var configuration: PhenixConfiguration? = null
        private set

    /**
     * Returns true if [PhenixCore] is successfully initialized.
     */
    val isInitialized get() = repository.isPhenixInitialized

    /**
     * Returns true if [PhenixCore] is initializing.
     */
    val isInitializing get() = repository.isPhenixInitializing

    val sdkVersion = BuildConfig.PHENIX_SDK_VERSION
    val sdkCode = BuildConfig.PHENIX_SDK_BUILD

    init {
        Timber.d("Initializing core: ${BuildConfig.VERSION_CODE}, ${BuildConfig.VERSION_NAME}")
        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(application)).build()
        component.inject(this)
        debugTree.setCore(this)

        // Observe repositories
        launchIO { repository.onError.collect { _onError.tryEmit(it) } }
        launchIO { repository.onEvent.collect { _onEvent.tryEmit(it) } }
        launchIO { repository.channels.collect { _channels.tryEmit(it) } }
        launchIO { repository.streams.collect { _streams.tryEmit(it) } }
        launchIO { repository.members.collect { _members.tryEmit(it) } }
        launchIO { repository.messages.collect { _messages.tryEmit(it) } }
        launchIO { repository.room.collect { _room.tryEmit(it)} }
        launchIO { repository.memberCount.collect { _memberCount.tryEmit(it) } }
        launchIO { repository.mediaState.collect { _mediaState.tryEmit(it) } }
    }

    /**
     * This function must be called before any other function of the [PhenixCore].
     * Use this to initialize the [PhenixCore] with an optional [PhenixConfiguration] object.
     * If no configuration is provided - default values will be used.
     * Once the [PhenixCore] is initialized either [onEvent] or [onError] will be notified about
     * the status of the initialization. During this period a good option is to show a "Splash Screen".
     *
     * Note that calling any other function before the initialization has been processed will
     * notify the [onError] with a error message [PhenixError.NOT_INITIALIZED].
     *
     * If the function is called twice while being initialized or after initialization,
     * then the [onError] will be notified with error message [PhenixError.ALREADY_INITIALIZING]
     * or [PhenixError.ALREADY_INITIALIZED].
     *
     * If the [PhenixCore] is successfully initialized, then the [onEvent] will be notified
     * with error [PhenixEvent.PHENIX_CORE_INITIALIZED].
     *
     * If the [PhenixCore] has failed to initialize, then the [onError] will be notified
     * with error [PhenixError.FAILED_TO_INITIALIZE].
     */
    fun init(config: PhenixConfiguration? = null) {
        if (repository.isPhenixInitializing) {
            Timber.d("Core already initializing")
            _onError.tryEmit(PhenixError.ALREADY_INITIALIZING)
            return
        }
        if (repository.isPhenixInitialized) {
            Timber.d("Core already initialized")
            _onError.tryEmit(PhenixError.ALREADY_INITIALIZED)
            return
        }
        configuration = config
        repository.init(configuration)
    }

    /**
     * Joins all channels that were provided with the [PhenixConfiguration] object
     * when initializing the [PhenixCore] class.
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * If the channel list in configuration is empty, the the [onError] will be notified
     * with error [PhenixError.CHANNEL_LIST_EMPTY].
     *
     * The [channels] list will be updated with the new [PhenixChannel] models when called.
     * The [PhenixChannel.channelState] variable will hold the
     * joining status for each of the channels.
     */
    fun joinAllChannels() {
        whenInitialized {
            if (configuration?.channelAliases?.isEmpty() == true && configuration?.streamIDs?.isEmpty() == true) {
                _onError.tryEmit(PhenixError.CHANNEL_LIST_EMPTY)
                return@whenInitialized
            }
            repository.joinAllChannels(
                configuration?.channelAliases ?: emptyList()
            )
        }
    }

    /**
     * Joins all streams that were provided with the [PhenixConfiguration] object
     * when initializing the [PhenixCore] class.
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * If the channel list in configuration is empty, the the [onError] will be notified
     * with error [PhenixError.CHANNEL_LIST_EMPTY].
     *
     * The [streams] list will be updated with the new [PhenixStream] models when called.
     * The [PhenixStream.streamState] variable will hold the
     * joining status for each of the stream.
     */
    fun joinAllStreams() {
        whenInitialized {
            if (configuration?.channelAliases?.isEmpty() == true && configuration?.streamIDs?.isEmpty() == true) {
                _onError.tryEmit(PhenixError.CHANNEL_LIST_EMPTY)
                return@whenInitialized
            }
            repository.joinAllStreams(
                configuration?.streamIDs ?: emptyList()
            )
        }
    }

    /**
     * Joins a channel by its alias with additional [PhenixChannelConfiguration].
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * The [channels] list will be updated with a new [PhenixChannel] model when called.
     * The [PhenixChannel.channelState] variable will hold the
     * joining status of the channel.
     */
    fun joinChannel(config: PhenixChannelConfiguration) {
        whenInitialized {
            repository.joinChannel(config)
        }
    }

    /**
     * Leaves a channel by its alias.
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * The [channels] list will be updated when channel is removed.
     */
    fun leaveChannel(channelAlias: String) {
        whenInitialized {
            repository.leaveChannel(channelAlias)
        }
    }

    /**
     * Leaves all joined channels.
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * The [channels] list will be updated when channels are removed.
     */
    fun leaveAllChannels() {
        Timber.d("Leave all channels")
        whenInitialized {
            repository.leaveAllChannels()
        }
    }

    /**
     * Joins a stream by its id with additional [PhenixStreamConfiguration].
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * The [streams] list will be updated with a new [PhenixStream] model when called.
     * The [PhenixStream.streamState] variable will hold the
     * joining status of the stream.
     */
    fun joinStream(config: PhenixStreamConfiguration) {
        whenInitialized {
            repository.joinStream(config)
        }
    }

    /**
     * Leaves a stream by its id.
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * The [streams] list will be updated when stream is removed.
     */
    fun leaveStream(streamID: String) {
        whenInitialized {
            repository.leaveStream(streamID)
        }
    }

    /**
     * Leaves all joined streams.
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * The [streams] list will be updated when streams are removed.
     */
    fun leaveAllStreams() {
        whenInitialized {
            repository.leaveAllStreams()
        }
    }

    /**
     * Starts to publish member media to the given channel with given [PhenixChannelConfiguration]
     * and [PhenixPublishConfiguration].
     *
     * If the publishing to channel fails then the [onError] will be notified
     * with [PhenixError.PUBLISH_CHANNEL_FAILED].
     */
    fun publishToChannel(
        configuration: PhenixChannelConfiguration,
        publishConfiguration: PhenixPublishConfiguration = PhenixPublishConfiguration()
    ) {
        whenInitialized {
            repository.publishToChannel(configuration, publishConfiguration)
        }
    }

    /**
     * Joins a room with given [PhenixRoomConfiguration].
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * If the room is joined successfully then the [onEvent] will be updated with [PhenixEvent.PHENIX_ROOM_JOINED]
     * otherwise the [onError] will be notified with [PhenixError.JOIN_ROOM_FAILED].
     */
    fun joinRoom(configuration: PhenixRoomConfiguration) {
        whenInitialized {
            repository.joinRoom(configuration)
        }
    }

    /**
     * Creates a room with given [PhenixRoomConfiguration].
     *
     * If the function is called prior the [PhenixCore] is initialized, then the [onError]
     * will be notified with error [PhenixError.NOT_INITIALIZED].
     *
     * If the room is created successfully then the [onEvent] will be updated with [PhenixEvent.PHENIX_ROOM_CREATED]
     * otherwise the [onError] will be notified with [PhenixError.CREATE_ROOM_FAILED].
     */
    fun createRoom(configuration: PhenixRoomConfiguration) {
        whenInitialized {
            repository.createRoom(configuration)
        }
    }

    /**
     * Leaves the currently joined room if exists.
     *
     * If the room is left successfully then the [onEvent] will be updated with [PhenixEvent.PHENIX_ROOM_LEFT]
     * otherwise the [onError] will be notified with [PhenixError.LEAVE_ROOM_FAILED].
     */
    fun leaveRoom() {
        whenInitialized {
            repository.leaveRoom()
        }
    }

    /**
     * Enables / Disables publishing. Must be called before calling any publish / preview functions.
     *
     * If the publishing is not enabled then the [onError] will be notified
     * with [PhenixError.PUBLISHING_NOT_ENABLED].
     */
    fun enablePublishing(publishingEnabled: Boolean) {
        whenInitialized {
            repository.enablePublishing(publishingEnabled)
        }
    }

    /**
     * Starts to publish member media to the given room with given [PhenixRoomConfiguration].
     *
     * If the publishing to room fails then the [onError] will be notified
     * with [PhenixError.PUBLISH_ROOM_FAILED].
     */
    fun publishToRoom(
        configuration: PhenixRoomConfiguration,
        publishConfiguration: PhenixPublishConfiguration = PhenixPublishConfiguration()
    ) {
        whenInitialized {
            repository.publishToRoom(configuration, publishConfiguration)
        }
    }

    fun observeVideoFrames(onFrame: (Bitmap) -> Bitmap) {
        whenInitialized {
            repository.observeVideoFrames(onFrame)
        }
    }

    /**
     * Changes the given [PhenixChannel.isSelected] value.
     * When the value is changed, then the [channels] flow will be updated.
     */
    fun selectChannel(alias: String, isSelected: Boolean) {
        whenInitialized {
            repository.selectChannel(alias, isSelected)
        }
    }

    /**
     * Changes the given [PhenixStream.isSelected] value.
     * When the value is changed, then the [streams] flow will be updated.
     */
    fun selectStream(alias: String, isSelected: Boolean) {
        whenInitialized {
            repository.selectStream(alias, isSelected)
        }
    }

    /**
     * Flips the camera facing for user media.
     * If the request fails - [onError] will be notified with [PhenixError.CAMERA_FLIP_FAILED].
     * If the request succeeds - [onEvent] will be notified with [PhenixEvent.CAMERA_FLIPPED].
     */
    fun flipCamera() {
        whenInitialized {
            repository.flipCamera()
        }
    }

    /**
     * Sets the camera facing to the desired mode.
     * If the request fails - [onError] will be notified with [PhenixError.CAMERA_FLIP_FAILED].
     * If the request succeeds - [onEvent] will be notified with [PhenixEvent.CAMERA_FLIPPED].
     */
    fun setCameraFacing(facing: PhenixFacingMode) {
        whenInitialized {
            repository.setCameraFacing(facing)
        }
    }

    /**
     * Starts video on the given [surfaceView] for the given channel alias / member ID if found.
     * If the [surfaceView] is null - the video renderer is stopped, else - started.
     */
    fun renderOnSurface(alias: String, surfaceView: SurfaceView?) {
        whenInitialized {
            repository.renderOnSurface(alias, surfaceView)
        }
    }

    /**
     * Sets or removes the [Renderer.FrameReadyForProcessingCallback] for the latest
     * media stream of the given channel alias / member ID and draws [Bitmap]s on the provided [imageView].
     *
     * If the [imageView] is null - the callback is removed, else - set.
     */
    fun renderOnImage(alias: String, imageView: ImageView?, configuration: PhenixFrameReadyConfiguration? = null) {
        whenInitialized {
            repository.renderOnImage(alias, imageView, configuration)
        }
    }

    /**
     * Starts video on the given [surfaceView] for the self user media.
     * If the [surfaceView] is null - the video renderer is stopped, else - started.
     */
    fun previewOnSurface(surfaceView: SurfaceView?) {
        whenInitialized {
            repository.previewOnSurface(surfaceView)
        }
    }

    /**
     * Sets or removes the [Renderer.FrameReadyForProcessingCallback] for the latest
     * media stream of self user media and draws [Bitmap]s on the provided [imageView].
     *
     * If the [imageView] is null - the callback is removed, else - set.
     */
    fun previewOnImage(imageView: ImageView?, configuration: PhenixFrameReadyConfiguration? = null) {
        whenInitialized {
            repository.previewOnImage(imageView, configuration)
        }
    }

    /**
     * Changes the given [PhenixChannel.isAudioEnabled] or [PhenixMember.isAudioEnabled] value.
     * When the value is changed, then the [channels] or [members] flow will be updated.
     */
    fun setAudioEnabled(alias: String, enabled: Boolean) {
        whenInitialized {
            repository.setAudioEnabled(alias, enabled)
        }
    }

    /**
     * Changes the self preview and publisher audio state.
     */
    fun setSelfAudioEnabled(enabled: Boolean) {
        whenInitialized {
            repository.setSelfAudioEnabled(enabled)
        }
    }

    /**
     * Changes the given [PhenixMember.isVideoEnabled] value.
     * When the value is changed, then the [members] flow will be updated.
     */
    fun setVideoEnabled(alias: String, enabled: Boolean) {
        whenInitialized {
            repository.setVideoEnabled(alias, enabled)
        }
    }

    /**
     * Changes the self preview and publisher video state.
     */
    fun setSelfVideoEnabled(enabled: Boolean) {
        whenInitialized {
            repository.setSelfVideoEnabled(enabled)
        }
    }

    /**
     * Creates [TimeShift] for the given alias or id at given [timestamp].
     * The [timestamp] value should be a UTC time in milliseconds.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun createTimeShift(alias: String, timestamp: Long) {
        whenInitialized {
            repository.createTimeShift(alias, timestamp)
        }
    }

    /**
     * Starts [TimeShift] seeking for the given alias or id with given [duration].
     * The [duration] value should be a length in milliseconds.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun startTimeShift(alias: String, duration: Long) {
        whenInitialized {
            repository.startTimeShift(alias, duration)
        }
    }

    /**
     * Seeks [TimeShift] for the given alias or id to the given [offset].
     * The [offset] value should be a length in milliseconds.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun seekTimeShift(alias: String, offset: Long) {
        whenInitialized {
            repository.seekTimeShift(alias, offset)
        }
    }

    /**
     * Starts [TimeShift] playback for the given alias or id.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun playTimeShift(alias: String) {
        whenInitialized {
            repository.playTimeShift(alias)
        }
    }

    /**
     * Pauses [TimeShift] playback for the given alias or id.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun pauseTimeShift(alias: String) {
        whenInitialized {
            repository.pauseTimeShift(alias)
        }
    }

    /**
     * Stops [TimeShift] playback for the given alias or id.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun stopTimeShift(alias: String) {
        whenInitialized {
            repository.stopTimeShift(alias)
        }
    }

    /**
     * Limits the bandwidth for the given alias or id with the given [bandwidth] value.
     * Use 0 to set the limit to unlimited, otherwise a positive value is required.
     *
     * For example:
     * Ultra Low Definition (ULD) is a value of: 80 000
     * Ultra High Definition (UHD) is a value of: 8500 000
     */
    fun limitBandwidth(alias: String, bandwidth: Long) {
        whenInitialized {
            repository.limitBandwidth(alias, bandwidth)
        }
    }

    /**
     * Release the bandwidth limiter for the given alias or id.
     */
    fun releaseBandwidthLimiter(alias: String) {
        whenInitialized {
            repository.releaseBandwidthLimiter(alias)
        }
    }

    /**
     * Subscribe for messages for the given channel alias or id and [PhenixMessageConfiguration].
     */
    fun subscribeForMessages(alias: String, configuration: PhenixMessageConfiguration) {
        whenInitialized {
            repository.subscribeForMessages(alias, configuration)
        }
    }

    /**
     * Stops the media publishing in the joined room.
     *
     * The [members] list will be updated when the [PhenixMember.isVideoEnabled] changes for your self member.
     */
    fun stopPublishingToRoom() {
        whenInitialized {
            repository.stopPublishingToRoom()
        }
    }

    /**
     * Stops the media publishing in the joined channel.
     *
     * The [channels] list will be updated when the [PhenixChannel.channelState] changes.
     */
    fun stopPublishingToChannel() {
        whenInitialized {
            repository.stopPublishingToChannel()
        }
    }

    /**
     * Sets a member audio volume level.
     * The level range is between 0.0 and 1.0 - anything outside of the bounds will be rounded
     * to the nearest value 0.0 or 1.0.
     */
    fun setAudioLevel(memberId: String, level: Float) {
        whenInitialized {
            repository.setAudioLevel(memberId, level)
        }
    }

    /**
     * Sends a message with given mime type.
     * If the message sending fails, then the [onError] will be notified
     * with error [PhenixError.SEND_MESSAGE_FAILED].
     */
    fun sendMessage(alias: String, message: String, mimeType: String) {
        whenInitialized {
            repository.sendMessage(alias, message, mimeType)
        }
    }

    /**
     * Changes the given [PhenixMember.isSelected] value.
     * When the value is changed, then the [members] flow will be updated.
     */
    fun selectMember(memberId: String, isSelected: Boolean) {
        whenInitialized {
            repository.selectMember(memberId, isSelected)
        }
    }

    /**
     * Subscribes to all room member streams if a room is joined and the room has any members.
     */
    fun subscribeToRoom() {
        whenInitialized {
            repository.subscribeToRoom()
        }
    }

    /**
     * Updates member role, state and / or name. If a `null` value is passed, then the property
     * won't be changed.
     */
    fun updateMember(memberId: String, role: PhenixMemberRole? = null, active: PhenixMemberState? = null, name: String? = null) {
        whenInitialized {
            repository.updateMember(memberId, role, active, name)
        }
    }

    /**
     * Collects the logs from the PhenixSDK
     */
    fun collectLogs(onCollected: (String) -> Unit) {
        whenInitialized {
            repository.collectLogs(onCollected)
        }
    }

    /**
     * Clears the repositories and disposes all instances
     */
    fun release() {
        repository.release()
    }

    private fun whenInitialized(onInitialized: () -> Unit) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        onInitialized()
    }

}
