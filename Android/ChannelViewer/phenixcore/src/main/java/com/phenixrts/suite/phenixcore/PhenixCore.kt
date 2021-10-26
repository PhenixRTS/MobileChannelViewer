/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.phenixrts.suite.phenixcore

import android.app.Application
import android.graphics.Bitmap
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.pcast.Renderer
import com.phenixrts.pcast.TimeShift
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.MemberRole
import com.phenixrts.room.MemberState
import com.phenixrts.suite.phenixcore.closedcaptions.PhenixClosedCaptionView
import com.phenixrts.suite.phenixcore.common.asPhenixConfiguration
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.debugmenu.DebugMenu
import com.phenixrts.suite.phenixcore.deeplink.models.PhenixDeepLinkConfiguration
import com.phenixrts.suite.phenixcore.injection.DaggerInjectionComponent
import com.phenixrts.suite.phenixcore.injection.InjectionComponent
import com.phenixrts.suite.phenixcore.injection.InjectionModule
import com.phenixrts.suite.phenixcore.repositories.core.PhenixCoreRepository
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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

    internal companion object {
        lateinit var component: InjectionComponent
            private set
    }

    private val _onError = MutableSharedFlow<PhenixError>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _onEvent = MutableSharedFlow<PhenixEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _channels = MutableSharedFlow<List<PhenixChannel>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _messages = MutableSharedFlow<List<PhenixMessage>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _members = MutableSharedFlow<List<PhenixMember>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var _memberCount = MutableSharedFlow<Long>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Holds the latest received [PhenixError].
     * This flow is updated every time any error happens inside the [PhenixCore].
     */
    val onError: SharedFlow<PhenixError> = _onError

    /**
     * Holds the latest received [PhenixEvent].
     * This flow is updated every time any significant event happens inside the [PhenixCore].
     */
    val onEvent: SharedFlow<PhenixEvent> = _onEvent

    /**
     * Holds the list of all channels and their states. This is a super-hot flow and
     * is updated whenever something changes in any given channel. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val channels: SharedFlow<List<PhenixChannel>> = _channels

    /**
     * Holds the list of all messages and their states. This is a super-hot flow and
     * is updated whenever something changes in the message list. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val messages: SharedFlow<List<PhenixMessage>> = _messages

    /**
     * Holds the list of all members and their states. This is a super-hot flow and
     * is updated whenever something changes for any given member. Use this with a diff-util
     * to filter out unwanted updates. Ideally bind this directly to your UI layer and let the
     * values auto-update the views.
     */
    val members: SharedFlow<List<PhenixMember>> = _members

    /**
     * Holds the count of actual member instances in the connected room.
     */
    val memberCount: SharedFlow<Long> = _memberCount

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

    init {
        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(application)).build()
        component.inject(this)

        // Observe repositories
        launchIO { repository.onError.collect { _onError.tryEmit(it) } }
        launchIO { repository.onEvent.collect { _onEvent.tryEmit(it) } }
        launchIO { repository.channels.collect { _channels.tryEmit(it) } }
        launchIO { repository.members.collect { _members.tryEmit(it) } }
        launchIO { repository.messages.collect { _messages.tryEmit(it) } }
        launchIO { repository.memberCount.collect { _memberCount.tryEmit(it) } }
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
            _onError.tryEmit(PhenixError.ALREADY_INITIALIZING)
            return
        }
        if (repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.ALREADY_INITIALIZED)
            return
        }
        configuration = config
        repository.init(configuration)
    }

    /**
     * This function must be called before any other function of the [PhenixCore].
     * Use this to initialize the [PhenixCore] with an optional [PhenixDeepLinkConfiguration] object.
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
    fun init(config: PhenixDeepLinkConfiguration) {
        init(config.asPhenixConfiguration())
    }

    /**
     * A convenience function to consume the event flow so that new subscribers don't receive
     * an already collected event value.
     */
    fun consumeLastEvent() {
        _onEvent.resetReplayCache()
    }

    /**
     * A convenience function to consume the error flow so that new subscribers don't receive
     * an already collected error value.
     */
    fun consumeLastError() {
        _onError.resetReplayCache()
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
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        configuration?.channels?.let { channels ->
            repository.joinAllChannels(channels)
        } ?: _onError.tryEmit(PhenixError.CHANNEL_LIST_EMPTY)
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
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.joinChannel(config)
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
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.joinRoom(configuration)
    }

    /**
     * Leaves the currently joined room if exists.
     *
     * If the room is left successfully then the [onEvent] will be updated with [PhenixEvent.PHENIX_ROOM_LEFT]
     * otherwise the [onError] will be notified with [PhenixError.LEAVE_ROOM_FAILED].
     */
    fun leaveRoom() {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.leaveRoom()
    }

    /**
     * Changes the given [PhenixChannel.isSelected] value.
     * When the value is changed, then the [channels] flow will be updated.
     */
    fun selectChannel(alias: String, isSelected: Boolean) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.selectChannel(alias, isSelected)
    }

    /**
     * Sets the [AndroidVideoRenderSurface] that is used by the [Renderer]
     * to draw the video on the given [surfaceView].
     * This won't have any effect until the [Renderer] is created and started.
     */
    fun renderOnSurface(alias: String, surfaceView: SurfaceView?) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.renderOnSurface(alias, surfaceView)
    }

    /**
     * Sets or removes the [Renderer.FrameReadyForProcessingCallback] for the latest
     * media stream of the given alias and draws [Bitmap]s on the provided [imageView].
     *
     * If the [imageView] is null - the callback is removed, else - set.
     */
    fun renderOnImage(alias: String, imageView: ImageView?) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.renderOnImage(alias, imageView)
    }

    /**
     * Changes the given [PhenixChannel.isAudioEnabled] or [PhenixMember.isAudioEnabled] value.
     * When the value is changed, then the [channels] or [members] flow will be updated.
     */
    fun setAudioEnabled(alias: String, enabled: Boolean) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.setAudioEnabled(alias, enabled)
    }

    /**
     * Changes the given [PhenixMember.isVideoEnabled] value.
     * When the value is changed, then the [members] flow will be updated.
     */
    fun setVideoEnabled(alias: String, enabled: Boolean) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.setVideoEnabled(alias, enabled)
    }

    /**
     * Creates [TimeShift] for the given alias at given [timestamp].
     * The [timestamp] value should be a UTC time in milliseconds.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun createTimeShift(alias: String, timestamp: Long) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.createTimeShift(alias, timestamp)
    }

    /**
     * Starts [TimeShift] seeking for the given alias with given [duration].
     * The [duration] value should be a length in milliseconds.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun startTimeShift(alias: String, duration: Long) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.startTimeShift(alias, duration)
    }

    /**
     * Seeks [TimeShift] for the given alias to the given [offset].
     * The [offset] value should be a length in milliseconds.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun seekTimeShift(alias: String, offset: Long) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.seekTimeShift(alias, offset)
    }

    /**
     * Starts [TimeShift] playback for the given alias.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun playTimeShift(alias: String) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.playTimeShift(alias)
    }

    /**
     * Pauses [TimeShift] playback for the given alias.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun pauseTimeShift(alias: String) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.pauseTimeShift(alias)
    }

    /**
     * Stops [TimeShift] playback for the given alias.
     *
     * When status of the [TimeShift] changes, the [channels] list will be updated.
     * Use the [PhenixChannel.timeShiftState] to check the current time shift state.
     * Use the [PhenixChannel.timeShiftHead] to check the current loop position.
     */
    fun stopTimeShift(alias: String) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.stopTimeShift(alias)
    }

    /**
     * Limits the bandwidth for the given alias with the given [bandwidth] value.
     * Use 0 to set the limit to unlimited, otherwise a positive value is required.
     *
     * For example:
     * Ultra Low Definition (ULD) is a value of: 80 000
     * Ultra High Definition (UHD) is a value of: 8500 000
     */
    fun limitBandwidth(alias: String, bandwidth: Long) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.limitBandwidth(alias, bandwidth)
    }

    /**
     * Release the bandwidth limiter for the given alias.
     */
    fun releaseBandwidthLimiter(alias: String) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.releaseBandwidthLimiter(alias)
    }

    /**
     * Subscribe to closed captions for the selected channel onto the provided [PhenixClosedCaptionView].
     *
     * The mime types for the closed captions are taken from the [PhenixConfiguration] which is obtained when
     * initializing the [PhenixCore] object.
     */
    fun subscribeToCC(alias: String, closedCaptionView: PhenixClosedCaptionView) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.subscribeToCC(alias, closedCaptionView)
    }

    /**
     * Adds listeners to the debug menu and delivers results back to UI.
     *
     * The [onError] will be called if log collection fails.
     * The [onEvent] will be called to indicate that the app chooser must be shown from the debug menu.
     */
    fun observeDebugMenu(debugMenu: DebugMenu, authority: String) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.observeDebugMenu(debugMenu, authority)
    }

    /**
     * Stops the media publishing in the joined room.
     *
     * The [members] list will be updated when the [PhenixMember.isVideoEnabled] changes for your self member.
     */
    fun stopPublishing() {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.stopPublishing()
    }

    /**
     * Sets a member audio volume level.
     * The level range is between 0.0 and 1.0 - anything outside of the bounds will be rounded
     * to the nearest value 0.0 or 1.0.
     */
    fun setAudioLevel(memberId: String, level: Float) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.setAudioLevel(memberId, level)
    }

    /**
     * Sends a message with given mime type.
     * If the message sending fails, then the [onError] will be notified
     * with error [PhenixError.SEND_MESSAGE_FAILED].
     */
    fun sendMessage(message: String, mimeType: String) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.sendMessage(message, mimeType)
    }

    /**
     * Changes the given [PhenixMember.isSelected] value.
     * When the value is changed, then the [members] flow will be updated.
     */
    fun selectMember(memberId: String, isSelected: Boolean) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.selectMember(memberId, isSelected)
    }

    /**
     * Starts to publish member media to the given room with given member role and name.
     *
     * If the publishing to room fails then the [_onError] will be notified
     * with [PhenixError.PUBLISH_ROOM_FAILED].
     */
    fun publishToRoom(alias: String, role: MemberRole, name: String?) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.publishToRoom(alias, role, name)
    }

    /**
     * Subscribes to all room member streams if a room is joined and the room has any members.
     */
    fun subscribeToRoom() {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.subscribeToRoom()
    }

    /**
     * Updates member role, state and / or name. If a `null` value is passed, then the property
     * won't be changed.
     */
    fun updateMember(memberId: String, role: MemberRole? = null, active: MemberState? = null, name: String? = null) {
        if (!repository.isPhenixInitialized) {
            _onError.tryEmit(PhenixError.NOT_INITIALIZED)
            return
        }
        repository.updateMember(memberId, role, active, name)
    }

}
