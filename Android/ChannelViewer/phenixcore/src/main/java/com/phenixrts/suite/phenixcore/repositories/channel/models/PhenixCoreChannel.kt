/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.channel.models

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.*
import com.phenixrts.pcast.*
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.chat.PhenixChatRepository
import com.phenixrts.suite.phenixcore.repositories.core.common.THUMBNAIL_DRAW_DELAY
import com.phenixrts.suite.phenixcore.repositories.core.common.isSeekable
import com.phenixrts.suite.phenixcore.repositories.core.common.onVideoFrameCallback
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.*

private const val TIME_SHIFT_RETRY_DELAY = 1000 * 10L
private const val TIME_SHIFT_START_WAIT_TIME = 1000 * 20L
private const val TIME_SHIFT_RETRY_COUNT = 10

internal data class PhenixCoreChannel(
    private val pCastExpress: PCastExpress,
    private val channelExpress: ChannelExpress,
    private val configuration: PhenixConfiguration,
    private val chatRepository: PhenixChatRepository,
    val channelAlias: String
) {
    private val videoRenderSurface = AndroidVideoRenderSurface()
    private var renderer: Renderer? = null
    private var expressSubscriber: ExpressSubscriber? = null
    private var roomService: RoomService? = null
    private var timeShift: TimeShift? = null
    private var bandwidthLimiter: Disposable? = null
    private var timeShiftDisposables = mutableSetOf<Disposable>()
    private var timeShiftSeekDisposables = mutableSetOf<Disposable>()
    private var isFirstFrameDrawn = false
    private var isRendering = false
    private var timeShiftCreateRetryCount = 0
    private var timeShiftStart = 0L
    private var streamImageView: ImageView? = null
    private var frameReadyConfiguration: PhenixFrameReadyConfiguration? = null
    private var isDisposed = false

    private val _onUpdated = ConsumableSharedFlow<Unit>()
    private val _onError = ConsumableSharedFlow<PhenixError>()

    val onUpdated = _onUpdated.asSharedFlow()
    val onError = _onError.asSharedFlow()

    var timeShiftState = PhenixTimeShiftState.IDLE
        private set
    var isSelected: Boolean = false
        private set
    var isAudioEnabled: Boolean = false
        private set
    val isVideoEnabled get() = renderer != null
    var timeShiftHead = 0L
        private set
    var channelState = PhenixChannelState.OFFLINE
        private set

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Timber.d("Time shift creating timed out")
        updateTimeShiftState(PhenixTimeShiftState.FAILED)
    }

    fun join(config: PhenixChannelConfiguration) {
        Timber.d("Joining channel with configuration: $configuration for: ${asString()}")
        channelState = PhenixChannelState.JOINING
        _onUpdated.tryEmit(Unit)
        joinChannel(config)
    }

    fun selectChannel(selected: Boolean) {
        Timber.d("Setting selected: $selected for: ${asString()}")
        isSelected = selected
        _onUpdated.tryEmit(Unit)
    }

    fun setAudioEnabled(enabled: Boolean) {
        Timber.d("Setting audio enabled: $enabled for: ${asString()}")
        isAudioEnabled = enabled
        if (enabled) {
            renderer?.unmuteAudio()
        } else {
            renderer?.muteAudio()
        }
        _onUpdated.tryEmit(Unit)
    }

    fun renderOnImage(imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        Timber.d("Renderer ${if (imageView == null) "disabled" else "enabled"} on image view for: ${asString()}")
        streamImageView = imageView
        frameReadyConfiguration = configuration
        if (!isRendering && imageView != null) {
            startRenderer()
        }
        expressSubscriber?.videoTracks?.lastOrNull()?.let { videoTrack ->
            val callback = if (streamImageView == null) null else onVideoFrameCallback(configuration) { bitmap ->
                drawFrameBitmap(bitmap)
            }
            if (callback == null) isFirstFrameDrawn = false
            renderer?.setFrameReadyCallback(videoTrack, callback)
        }
    }

    fun renderOnSurface(surfaceView: SurfaceView?) {
        Timber.d("Renderer ${if (surfaceView == null) "disabled" else "enabled"} on surface view for: ${asString()}")
        videoRenderSurface.setSurfaceHolder(surfaceView?.holder)
        if (surfaceView == null) {
            renderer?.stop()
            renderer = null
            isRendering = false
        } else if (!isRendering) {
            startRenderer()
        }
    }

    fun createTimeShift(timestamp: Long) {
        if (!renderer.isSeekable()) {
            Timber.d("Channel has no time shift capability")
            updateTimeShiftState(PhenixTimeShiftState.FAILED)
            return
        }
        timeShiftStart = timestamp
        updateTimeShiftState(PhenixTimeShiftState.STARTING)
        releaseTimeShift()
        val utcTime = System.currentTimeMillis()
        val offset = utcTime - timeShiftStart
        Timber.d("Creating time shift with offset: $offset for: ${asString()}")
        timeShift = renderer?.seek(Date(offset))
        Timber.d("Time shift created: ${timeShift?.startTime?.time} : ${0}, offset: $offset, for: ${asString()}")
        subscribeToTimeShiftReadyForPlaybackObservable()
    }

    fun startTimeShift(duration: Long) {
        if (!renderer.isSeekable()) {
            Timber.d("Channel has no time shift capability")
            updateTimeShiftState(PhenixTimeShiftState.FAILED)
            return
        }
        Timber.d("Starting time shift for: ${asString()}")
        timeShift?.loop(duration)
        updateTimeShiftState(PhenixTimeShiftState.REPLAYING)
    }

    fun stopTimeShift() {
        Timber.d("Stopping time shift for: ${asString()}")
        timeShift?.stop()
        updateTimeShiftState(PhenixTimeShiftState.READY)
    }

    fun seekTimeShift(offset: Long) {
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        updateTimeShiftState(PhenixTimeShiftState.STARTING)
        timeShift?.run {
            Timber.d("Seeking time shift at: $offset for: ${asString()}")
            seek(offset, SeekOrigin.BEGINNING)?.subscribe { status ->
                Timber.d("Time shift seek status: $status at $offset for: ${asString()}")
                if (status == RequestStatus.OK) {
                    updateTimeShiftState(PhenixTimeShiftState.SOUGHT)
                } else {
                    updateTimeShiftState(PhenixTimeShiftState.FAILED)
                }
            }?.run { timeShiftSeekDisposables.add(this) }
        }
    }

    fun playTimeShift() {
        timeShift?.run {
            Timber.d("Playing time shift for: ${asString()}")
            play()
            updateTimeShiftState(PhenixTimeShiftState.REPLAYING)
        }
    }

    fun pauseTimeShift() {
        timeShift?.run {
            Timber.d("Pausing time shift for: ${asString()}")
            pause()
            updateTimeShiftState(PhenixTimeShiftState.PAUSED)
        }
    }

    fun limitBandwidth(bandwidth: Long) {
        Timber.d("Limiting bandwidth for: ${asString()}")
        expressSubscriber?.videoTracks?.getOrNull(0)?.limitBandwidth(bandwidth)?.let { disposable ->
            bandwidthLimiter = disposable
        }
    }

    fun releaseBandwidthLimiter() {
        Timber.d("Releasing bandwidth for: ${asString()}")
        bandwidthLimiter?.dispose()
        bandwidthLimiter = null
    }

    fun subscribeForMessages(configuration: PhenixMessageConfiguration) {
        if (roomService == null) return
        chatRepository.subscribeForMessages(channelAlias, roomService!!, configuration)
    }

    fun release() {
        releaseTimeShift()
        releaseBandwidthLimiter()
        renderer?.stop()
        expressSubscriber?.dispose()
        renderer?.dispose()
        expressSubscriber = null
        renderer = null
        streamImageView = null
        roomService = null
        chatRepository.disposeChatService(channelAlias)
        isDisposed = true
        Timber.d("Channel disposed")
    }

    private fun joinChannel(config: PhenixChannelConfiguration) {
        channelExpress.joinChannel(getChannelConfiguration(config), joinCoreChannel@ { requestStatus: RequestStatus?, service: RoomService? ->
            if (isDisposed) return@joinCoreChannel
            Timber.d("Channel joined with status: $requestStatus for: ${asString()}")
            if (requestStatus == RequestStatus.OK) {
                channelState = PhenixChannelState.NO_STREAM
                roomService = service
            } else {
                channelState = PhenixChannelState.OFFLINE
            }
            _onUpdated.tryEmit(Unit)
        }, { requestStatus: RequestStatus?, subscriber: ExpressSubscriber?, expressRenderer: Renderer? ->
            launchIO {
                if (isDisposed) return@launchIO
                Timber.d("Stream re-started: $requestStatus for: ${asString()}")
                if (requestStatus == RequestStatus.OK) {
                    renderer?.dispose()
                    renderer = null
                    expressSubscriber = subscriber
                    renderer = expressRenderer
                    isRendering = false
                    renderOnImage(streamImageView, frameReadyConfiguration)
                    channelState = PhenixChannelState.STREAMING
                } else {
                    channelState = PhenixChannelState.NO_STREAM
                }
                _onUpdated.tryEmit(Unit)
            }
        })
    }

    private fun createRenderer() {
        Timber.d("Creating renderer for: ${asString()}")
        renderer?.dispose()
        renderer = expressSubscriber?.createRenderer(RendererOptions().apply {
            aspectRatioMode = AspectRatioMode.LETTERBOX
        })
        if (renderer == null) {
            _onError.tryEmit(PhenixError.CREATE_RENDERER_FAILED)
        }
    }

    private fun startRenderer() {
        if (renderer == null) {
            createRenderer()
        }
        Timber.d("Starting renderer for: ${asString()}")
        renderer?.start(videoRenderSurface)?.let { state ->
            Timber.d("Renderer started with status: $state")
            isRendering = state == RendererStartStatus.OK
            if (state != RendererStartStatus.OK) {
                _onError.tryEmit(PhenixError.RENDERING_FAILED)
            } else {
                _onUpdated.tryEmit(Unit)
            }
        }
    }

    private fun updateTimeShiftState(state: PhenixTimeShiftState) {
        if (state == PhenixTimeShiftState.STARTING) {
            timeoutHandler.postDelayed(timeoutRunnable, TIME_SHIFT_START_WAIT_TIME)
        } else {
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }
        timeShiftState = state
        _onUpdated.tryEmit(Unit)
    }

    private fun getChannelConfiguration(config: PhenixChannelConfiguration): JoinChannelOptions {
        var builder = RoomExpressFactory.createJoinRoomOptionsBuilder()
            .withRoomAlias(channelAlias)
        val streamToken = config.streamToken ?: configuration.streamToken
        if (streamToken.isNullOrBlank() && config.channelCapabilities.isNotEmpty()) {
            Timber.d("Adding capabilities: ${config.channelCapabilities}")
            // TODO: This crashes the app for some reason if a room is joined prior joining a channel
            builder = builder.withCapabilities(config.channelCapabilities.toTypedArray())
        }
        val joinRoomOptions = builder.buildJoinRoomOptions()
        var channelOptionsBuilder = ChannelExpressFactory
            .createJoinChannelOptionsBuilder()
            .withJoinRoomOptions(joinRoomOptions)
            .withRendererOptions(RendererOptions().apply {
                aspectRatioMode = AspectRatioMode.LETTERBOX
            })
            .withRenderer(videoRenderSurface)
        if (!streamToken.isNullOrBlank()) {
            Timber.d("Adding stream token")
            channelOptionsBuilder = channelOptionsBuilder.withStreamToken(streamToken)
                .withSkipRetryOnUnauthorized()
        }
        return channelOptionsBuilder.buildJoinChannelOptions()
    }

    private fun subscribeToTimeShiftReadyForPlaybackObservable() {
        updateTimeShiftState(PhenixTimeShiftState.STARTING)
        timeShift?.observableReadyForPlaybackStatus?.subscribe { isReady ->
            if (timeShiftState == PhenixTimeShiftState.REPLAYING || isDisposed) return@subscribe
            val state = if (isReady) PhenixTimeShiftState.READY else PhenixTimeShiftState.STARTING
            if (isReady) timeShiftCreateRetryCount = 0
            updateTimeShiftState(state)
        }?.run { timeShiftDisposables.add(this) }
        timeShift?.observablePlaybackHead?.subscribe { head ->
            val offset = head.time - (timeShift?.startTime?.time ?: 0)
            if (timeShiftHead != offset) {
                timeShiftHead = offset
                _onUpdated.tryEmit(Unit)
            }
        }?.run { timeShiftDisposables.add(this) }
        timeShift?.observableFailure?.subscribe { status ->
            launchIO {
                if (isDisposed) return@launchIO
                Timber.d("Time shift failure: $status, retryCount: $timeShiftCreateRetryCount for: ${asString()}")
                releaseTimeShift()
                if (timeShiftCreateRetryCount < TIME_SHIFT_RETRY_COUNT) {
                    timeShiftCreateRetryCount++
                    updateTimeShiftState(PhenixTimeShiftState.STARTING)
                    delay(TIME_SHIFT_RETRY_DELAY)
                    createTimeShift(timeShiftStart)
                } else {
                    Timber.d("Failed to create time shift in $timeShiftCreateRetryCount tries")
                    timeShiftCreateRetryCount = 0
                    updateTimeShiftState(PhenixTimeShiftState.FAILED)
                }
            }
        }?.run { timeShiftDisposables.add(this) }
    }

    private fun drawFrameBitmap(bitmap: Bitmap) {
        try {
            launchMain {
                if (!isSelected || bitmap.isRecycled || isDisposed) return@launchMain
                if (isFirstFrameDrawn) delay(THUMBNAIL_DRAW_DELAY)
                streamImageView?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
                isFirstFrameDrawn = true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to draw bitmap for: ${asString()}")
        }
    }

    private fun releaseTimeShift() {
        val disposed = timeShiftDisposables.isNotEmpty() || timeShiftSeekDisposables.isNotEmpty()
        timeShiftDisposables.forEach { it.dispose() }
        timeShiftDisposables.clear()
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        timeShift?.dispose()
        timeShift = null
        if (disposed) {
            Timber.d("Time shift released for: ${asString()}")
        }
    }

    private fun asString() = this@PhenixCoreChannel.toString()

    override fun toString(): String {
        return "{\"alias\":\"$channelAlias\"," +
                "\"timeShiftState\":\"$timeShiftState\"," +
                "\"timeShiftHead\":\"$timeShiftHead\"," +
                "\"timeShiftStart\":\"$timeShiftStart\"," +
                "\"isSeekable\":\"${renderer.isSeekable()}\"," +
                "\"channelState\":\"$channelState\"," +
                "\"isRendering\":\"$isRendering\"," +
                "\"isAudioEnabled\":\"$isAudioEnabled\"," +
                "\"isVideoEnabled\":\"$isVideoEnabled\"," +
                "\"isDisposed\":\"$isDisposed\"," +
                "\"isSelected\":\"$isSelected\"}"
    }
}
