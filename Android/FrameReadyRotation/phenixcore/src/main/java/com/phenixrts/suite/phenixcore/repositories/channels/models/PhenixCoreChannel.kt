/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.channels.models

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.*
import com.phenixrts.media.video.android.AndroidVideoFrame
import com.phenixrts.pcast.*
import com.phenixrts.pcast.android.AndroidReadVideoFrameCallback
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixcore.closedcaptions.PhenixClosedCaptionView
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber
import java.util.*

private const val TIME_SHIFT_RETRY_DELAY = 1000 * 10L
private const val TIME_SHIFT_START_WAIT_TIME = 1000 * 20L
private const val THUMBNAIL_DRAW_DELAY = 100L

internal data class PhenixCoreChannel(
    private val channelExpress: ChannelExpress,
    private val configuration: PhenixConfiguration,
    val alias: String
) {
    private val videoRenderSurface = AndroidVideoRenderSurface()
    private var renderer: Renderer? = null
    private var expressSubscriber: ExpressSubscriber? = null
    private var roomService: RoomService? = null
    private var timeShift: TimeShift? = null
    private var bandwidthLimiter: Disposable? = null
    private var timeShiftDisposables = mutableListOf<Disposable>()
    private var timeShiftSeekDisposables = mutableListOf<Disposable>()
    private var isFirstFrameDrawn = false
    private var isRendering: Boolean = false
    private var timeShiftCreateRetryCount = 0
    private var timeShiftStart = 0L
    private var channelSurfaceView: SurfaceView? = null
    private var channelImageView: ImageView? = null
    private var frameReadyConfiguration: PhenixFrameReadyConfiguration? = null

    private val _onUpdated = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _onError = MutableSharedFlow<PhenixError>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val onUpdated: SharedFlow<Unit> = _onUpdated
    val onError: SharedFlow<PhenixError> = _onError

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

    private val frameCallback = Renderer.FrameReadyForProcessingCallback { frameNotification ->
        if (!isSelected) return@FrameReadyForProcessingCallback
        frameNotification?.read(object : AndroidReadVideoFrameCallback() {
            override fun onVideoFrameEvent(videoFrame: AndroidVideoFrame?) {
                videoFrame?.bitmap?.let { bitmap ->
                    drawFrameBitmap(bitmap.prepareBitmap())
                }
            }
        })
    }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        updateTimeShiftState(PhenixTimeShiftState.FAILED)
    }

    fun join(config: PhenixChannelConfiguration) {
        Timber.d("Joining channel with configuration: $configuration for: ${asString()}")
        channelState = PhenixChannelState.JOINING
        _onUpdated.tryEmit(Unit)
        channelExpress.joinChannel(getChannelConfiguration(config), { requestStatus: RequestStatus?, service: RoomService? ->
            Timber.d("Channel joined with status: $requestStatus for: ${asString()}")
            if (requestStatus == RequestStatus.OK) {
                channelState = PhenixChannelState.NO_STREAM
                roomService = service
            } else {
                channelState = PhenixChannelState.OFFLINE
            }
            _onUpdated.tryEmit(Unit)
        }, { requestStatus: RequestStatus?, subscriber: ExpressSubscriber?, expressRenderer: Renderer? ->
            Timber.d("Stream re-started: $requestStatus for: ${asString()}")
            if (requestStatus == RequestStatus.OK) {
                expressSubscriber?.stop()
                renderer?.stop()
                expressSubscriber?.dispose()
                renderer?.dispose()
                expressSubscriber = subscriber
                renderer = expressRenderer
                isRendering = renderer != null
                renderOnImage(channelImageView, frameReadyConfiguration)
                channelState = PhenixChannelState.STREAMING
            } else {
                channelState = PhenixChannelState.NO_STREAM
            }
            _onUpdated.tryEmit(Unit)
        })
    }

    private fun createRenderer() {
        Timber.d("Creating renderer for: ${asString()}")
        renderer?.dispose()
        renderer = expressSubscriber?.createRenderer()
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
            isRendering = state == RendererStartStatus.OK
            if (state != RendererStartStatus.OK) {
                _onError.tryEmit(PhenixError.RENDERING_FAILED)
            } else {
                _onUpdated.tryEmit(Unit)
            }
        }
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
        channelImageView = imageView
        frameReadyConfiguration = configuration
        if (!isRendering) {
            startRenderer()
        }
        expressSubscriber?.videoTracks?.lastOrNull()?.let { videoTrack ->
            val callback = if (channelImageView == null) null else frameCallback
            if (callback == null) isFirstFrameDrawn = false
            renderer?.setFrameReadyCallback(videoTrack, null)
            renderer?.setFrameReadyCallback(videoTrack, callback)
        }
    }

    fun renderOnSurface(surfaceView: SurfaceView?) {
        Timber.d("Renderer ${if (surfaceView == null) "disabled" else "enabled"} on surface view for: ${asString()}")
        channelSurfaceView = surfaceView
        videoRenderSurface.setSurfaceHolder(surfaceView?.holder)
        if (!isRendering) {
            startRenderer()
        }
    }

    fun createTimeShift(timestamp: Long) {
        if (renderer?.isSeekable == false) {
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
        if (renderer?.isSeekable == false) {
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

    fun subscribeToCC(closedCaptionView: PhenixClosedCaptionView) {
        roomService?.run {
            Timber.d("Subscribing and rendering CC messages")
            closedCaptionView.subscribe(this, configuration.mimeTypes)
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
            .withRoomAlias(alias)
        if (configuration.authToken.isNullOrBlank() && config.channelCapabilities.isNotEmpty()) {
            Timber.d("Adding capabilities: ${config.channelCapabilities}")
            // TODO: This crashes the app for some reason if a room is joined prior joining a channel
            builder = builder.withCapabilities(config.channelCapabilities.toTypedArray())
        }
        val joinRoomOptions = builder.buildJoinRoomOptions()
        var factory = ChannelExpressFactory
            .createJoinChannelOptionsBuilder()
            .withJoinRoomOptions(joinRoomOptions)
            .withRendererOptions(RendererOptions().apply {
                aspectRatioMode = AspectRatioMode.LETTERBOX
            })
            .withRenderer(videoRenderSurface)
        if (!config.streamToken.isNullOrBlank()) {
            Timber.d("Adding stream token: ${!config.streamToken.isNullOrBlank()}")
            factory = factory.withStreamToken(config.streamToken)
                .withSkipRetryOnUnauthorized()
        }
        return factory.buildJoinChannelOptions()
    }

    private fun subscribeToTimeShiftReadyForPlaybackObservable() {
        updateTimeShiftState(PhenixTimeShiftState.STARTING)
        timeShift?.observableReadyForPlaybackStatus?.subscribe { isReady ->
            if (timeShiftState == PhenixTimeShiftState.REPLAYING) return@subscribe
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
                Timber.d("Time shift failure: $status, retryCount: $timeShiftCreateRetryCount for: ${asString()}")
                releaseTimeShift()
                if (timeShiftCreateRetryCount < timeShiftStart / TIME_SHIFT_RETRY_DELAY) {
                    timeShiftCreateRetryCount++
                    updateTimeShiftState(PhenixTimeShiftState.STARTING)
                    delay(TIME_SHIFT_RETRY_DELAY)
                    createTimeShift(timeShiftStart)
                } else {
                    timeShiftCreateRetryCount = 0
                    updateTimeShiftState(PhenixTimeShiftState.FAILED)
                }
            }
        }?.run { timeShiftDisposables.add(this) }
    }

    private fun Bitmap.prepareBitmap(): Bitmap {
        if (frameReadyConfiguration == null) return this
        return try {
            val newWidth = frameReadyConfiguration!!.width.takeIf { it < width } ?: width
            val newHeight = frameReadyConfiguration!!.height.takeIf { it < height } ?: height
            val matrix = Matrix()
            if (newWidth > 0 && newHeight > 0) {
                val scaleWidth = newWidth / width.toFloat()
                val scaleHeight = newHeight / height.toFloat()
                matrix.postScale(scaleWidth, scaleHeight)
            }
            matrix.postRotate(frameReadyConfiguration!!.rotation)
            Bitmap.createBitmap(this, 0, 0, newWidth, newHeight, matrix, true)
        } catch (e: Exception) {
            Timber.d(e, "Failed to prepare bitmap for: $frameReadyConfiguration, $width, $height")
            this
        }
    }

    private fun drawFrameBitmap(bitmap: Bitmap) {
        try {
            launchMain {
                if (!isSelected || bitmap.isRecycled) return@launchMain
                if (isFirstFrameDrawn) delay(THUMBNAIL_DRAW_DELAY)
                channelImageView?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
                isFirstFrameDrawn = true
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed to draw bitmap for: ${asString()}")
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
        return "{\"alias\":\"$alias\"," +
                "\"timeShiftState\":\"$timeShiftState\"," +
                "\"timeShiftHead\":\"$timeShiftHead\"," +
                "\"timeShiftStart\":\"$timeShiftStart\"," +
                "\"isSeekable\":\"${renderer?.isSeekable}\"," +
                "\"channelState\":\"$channelState\"," +
                "\"isRendering\":\"$isRendering\"," +
                "\"isAudioEnabled\":\"$isAudioEnabled\"," +
                "\"isVideoEnabled\":\"$isVideoEnabled\"," +
                "\"isSelected\":\"$isSelected\"}"
    }
}
