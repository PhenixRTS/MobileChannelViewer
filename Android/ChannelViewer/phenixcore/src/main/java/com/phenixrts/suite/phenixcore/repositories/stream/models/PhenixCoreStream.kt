/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.stream.models

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.ExpressSubscriber
import com.phenixrts.express.PCastExpress
import com.phenixrts.express.PCastExpressFactory
import com.phenixrts.express.SubscribeOptions
import com.phenixrts.media.video.android.AndroidVideoFrame
import com.phenixrts.pcast.*
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.core.common.THUMBNAIL_DRAW_DELAY
import com.phenixrts.suite.phenixcore.repositories.core.common.isSeekable
import com.phenixrts.suite.phenixcore.repositories.core.common.onVideoFrameCallback
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

private const val TIME_SHIFT_RETRY_DELAY = 1000 * 10L
private const val TIME_SHIFT_START_WAIT_TIME = 1000 * 20L
private const val TIME_SHIFT_RETRY_COUNT = 10

data class PhenixCoreStream(
    private val pCastExpress: PCastExpress,
    private val configuration: PhenixConfiguration,
    val streamID: String
) {

    private val videoRenderSurface = AndroidVideoRenderSurface()
    private var renderer: Renderer? = null
    private var expressSubscriber: ExpressSubscriber? = null
    private var timeShift: TimeShift? = null
    private var timeShiftDisposables = mutableSetOf<Disposable>()
    private var timeShiftSeekDisposables = mutableSetOf<Disposable>()
    private var isFirstFrameDrawn = false
    private var isRendering = false
    private var timeShiftStart: Long = 0
    private var timeShiftCreateRetryCount = 0
    private var lastRenderedBitmap: Bitmap? = null
    private var isTimeShiftPaused = false
    private var streamImageView: ImageView? = null
    private var frameReadyConfiguration: PhenixFrameReadyConfiguration? = null

    private val _onUpdated = ConsumableSharedFlow<Unit>()
    private val _onError = ConsumableSharedFlow<PhenixError>()

    val onUpdated = _onUpdated.asSharedFlow()
    val onError = _onError.asSharedFlow()

    private val lastFrameCallback = Renderer.LastFrameRenderedReceivedCallback { _, videoFrame ->
        (videoFrame as? AndroidVideoFrame)?.bitmap?.let { bitmap ->
            lastRenderedBitmap?.recycle()
            if (isTimeShiftPaused) {
                lastRenderedBitmap = bitmap.copy(bitmap.config, bitmap.isMutable)
                restoreLastBitmap()
            }
        }
    }

    var timeShiftState = PhenixTimeShiftState.IDLE
        private set
    var isSelected: Boolean = false
        private set
    var isAudioEnabled: Boolean = false
        private set
    val isVideoEnabled get() = renderer != null
    var timeShiftHead = 0L
        private set
    var streamState = PhenixStreamState.OFFLINE
        private set

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        updateTimeShiftState(PhenixTimeShiftState.FAILED)
    }

    fun join(config: PhenixStreamConfiguration) {
        Timber.d("Joining channel with configuration: $configuration for: ${asString()}")
        streamState = PhenixStreamState.JOINING
        _onUpdated.tryEmit(Unit)
        pCastExpress.subscribe(getStreamOptions(config)) { requestStatus: RequestStatus?, subscriber: ExpressSubscriber?, _ ->
            launchIO {
                Timber.d("Stream re-started: $requestStatus for: ${asString()}")
                if (requestStatus == RequestStatus.OK) {
                    renderer?.dispose()
                    renderer = null
                    expressSubscriber = subscriber
                    isRendering = false
                    renderOnImage(streamImageView, frameReadyConfiguration)
                    streamState = PhenixStreamState.STREAMING
                } else {
                    streamState = PhenixStreamState.NO_STREAM
                }
                _onUpdated.tryEmit(Unit)
            }
        }
    }

    fun selectStream(selected: Boolean) {
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
        if (isTimeShiftPaused) {
            restoreLastBitmap()
        }
        expressSubscriber?.videoTracks?.lastOrNull()?.let { videoTrack ->
            val callback = if (streamImageView == null) null else onVideoFrameCallback(configuration) { bitmap ->
                drawFrameBitmap(bitmap)
            }
            if (callback == null) isFirstFrameDrawn = false
            renderer?.setFrameReadyCallback(videoTrack, callback)
            renderer?.setLastVideoFrameRenderedReceivedCallback(lastFrameCallback)
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
        Timber.d("Will create time shift: ${renderer.isSeekable()}")
        if (!renderer.isSeekable()) {
            updateTimeShiftState(PhenixTimeShiftState.FAILED)
            return
        }
        timeShiftStart = timestamp
        updateTimeShiftState(PhenixTimeShiftState.STARTING)
        releaseTimeShift()
        val utcTime = System.currentTimeMillis()
        val offset = utcTime - timeShiftStart
        Timber.d("Creating time shift with offset: $offset for: ${asString()}")
        timeShift = renderer?.seek(0, SeekOrigin.BEGINNING)
        Timber.d("Time shift created: ${timeShift?.startTime?.time} : ${0}, offset: $offset, for: ${asString()}")
        subscribeToTimeShiftObservables()
    }

    fun seekTimeShift(offset: Long) = launchMain {
        timeShiftSeekDisposables.forEach { it.dispose() }
        timeShiftSeekDisposables.clear()
        updateTimeShiftState(PhenixTimeShiftState.STARTING)
        pauseTimeShift()
        timeShift?.run {
            Timber.d("Seeking time shift at: $offset for: ${asString()}")
            seek(offset, SeekOrigin.BEGINNING)?.subscribe { status ->
                launchMain scope@{
                    if (status == RequestStatus.OK) {
                        updateTimeShiftState(PhenixTimeShiftState.SOUGHT)
                    } else {
                        updateTimeShiftState(PhenixTimeShiftState.FAILED)
                    }
                }
            }?.run { timeShiftSeekDisposables.add(this) }
        }
    }

    fun startTimeShift(duration: Long) {
        if (!renderer.isSeekable()) {
            updateTimeShiftState(PhenixTimeShiftState.FAILED)
            return
        }
        Timber.d("Starting time shift for: ${asString()}")
        isTimeShiftPaused = false
        timeShift?.loop(duration)
        updateTimeShiftState(PhenixTimeShiftState.REPLAYING)
    }

    fun stopTimeShift() {
        Timber.d("Stopping time shift for: ${asString()}")
        isTimeShiftPaused = true
        timeShift?.stop()
        updateTimeShiftState(PhenixTimeShiftState.READY)
    }

    fun pauseTimeShift() {
        Timber.d("Pausing time shift for: ${toString()}")
        isTimeShiftPaused = true
        timeShift?.pause()
        renderer?.requestLastVideoFrameRendered()
        updateTimeShiftState(PhenixTimeShiftState.PAUSED)
    }

    fun playTimeShift() {
        Timber.d("Playing time shift for: ${toString()}")
        isTimeShiftPaused = false
        lastRenderedBitmap?.recycle()
        timeShift?.play()
        updateTimeShiftState(PhenixTimeShiftState.REPLAYING)
    }

    fun release() {
        releaseTimeShift()
        renderer?.stop()
        expressSubscriber?.dispose()
        renderer?.dispose()
        expressSubscriber = null
        renderer = null
        streamImageView = null
        Timber.d("Stream released")
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
        renderer?.startSuspended(videoRenderSurface)?.let { state ->
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

    private fun getStreamOptions(config: PhenixStreamConfiguration): SubscribeOptions {
        var subscribeOptionsBuilder = PCastExpressFactory.createSubscribeOptionsBuilder()
            .withStreamId(config.streamID ?: streamID)
        val streamToken = config.streamToken ?: configuration.streamToken
        if (streamToken.isNullOrBlank() && config.capabilities.isNotEmpty()) {
            Timber.d("Adding capabilities: ${config.capabilities}")
            subscribeOptionsBuilder = subscribeOptionsBuilder.withCapabilities(config.capabilities.toTypedArray())
        } else if (!streamToken.isNullOrBlank()) {
            Timber.d("Adding stream token: $streamToken")
            subscribeOptionsBuilder = subscribeOptionsBuilder.withStreamToken(streamToken)
                .withSkipRetryOnUnauthorized()
        }
        return subscribeOptionsBuilder.buildSubscribeOptions()
    }

    private fun subscribeToTimeShiftObservables() {
        Timber.d("Subscribing to time shift observables")
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
                if (timeShiftCreateRetryCount < TIME_SHIFT_RETRY_COUNT) {
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
        timeShift?.observableEnded?.subscribe { hasEnded ->
            launchIO {
                if (hasEnded) {
                    Timber.d("Time shift ended: $hasEnded, ${asString()}")
                    updateTimeShiftState(PhenixTimeShiftState.IDLE)
                    restoreLastBitmap()
                }
            }
        }?.run { timeShiftDisposables.add(this) }
    }

    private fun drawFrameBitmap(bitmap: Bitmap) {
        try {
            launchMain {
                if (!isSelected || bitmap.isRecycled) return@launchMain
                if (isFirstFrameDrawn) delay(THUMBNAIL_DRAW_DELAY)
                lastRenderedBitmap = bitmap.copy(bitmap.config, bitmap.isMutable)
                streamImageView?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
                isFirstFrameDrawn = true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to draw bitmap for: ${asString()}")
        }
    }

    private fun restoreLastBitmap() = launchMain {
        lastRenderedBitmap?.takeIf { !it.isRecycled }?.let { bitmap ->
            Timber.d("Restoring bitmap for: ${asString()}")
            streamImageView?.setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
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

    private fun asString() = this@PhenixCoreStream.toString()

    override fun toString(): String {
        return "{\"streamID\":\"$streamID\"," +
                "\"timeShiftState\":\"$timeShiftState\"," +
                "\"timeShiftHead\":\"$timeShiftHead\"," +
                "\"timeShiftStart\":\"$timeShiftStart\"," +
                "\"isSeekable\":\"${renderer.isSeekable()}\"," +
                "\"channelState\":\"$streamState\"," +
                "\"isRendering\":\"$isRendering\"," +
                "\"isAudioEnabled\":\"$isAudioEnabled\"," +
                "\"isVideoEnabled\":\"$isVideoEnabled\"," +
                "\"isSelected\":\"$isSelected\"}"
    }
}
