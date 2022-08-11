/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.usermedia

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.PCastExpress
import com.phenixrts.media.video.android.AndroidVideoFrame
import com.phenixrts.pcast.Renderer
import com.phenixrts.pcast.RendererStartStatus
import com.phenixrts.pcast.UserMediaStream
import com.phenixrts.pcast.android.AndroidReadVideoFrameCallback
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.repositories.core.common.FAILURE_TIMEOUT
import com.phenixrts.suite.phenixcore.repositories.core.common.drawFrameBitmap
import com.phenixrts.suite.phenixcore.repositories.core.common.getUserMedia
import com.phenixrts.suite.phenixcore.repositories.core.common.getUserMediaOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.prepareBitmap
import com.phenixrts.suite.phenixcore.repositories.core.common.rendererOptions
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

class UserMediaRepository(
    private val pCastExpress: PCastExpress,
    private val configuration: PhenixConfiguration,
    private val onMicrophoneFailure: () -> Unit,
    private val onCameraFailure: () -> Unit,
) {

    private val videoRenderSurface by lazy { AndroidVideoRenderSurface() }
    private var selfVideoRenderer: Renderer? = null
    private var userMediaStream: UserMediaStream? = null
    private var selfPreviewImageView: ImageView? = null
    private var selfPreviewConfiguration: PhenixFrameReadyConfiguration? = null
    private var isFirstFrameDrawn = false
    private var currentPublishConfiguration = PhenixPublishConfiguration()
    private var onVideoFrame: ((Bitmap) -> Bitmap)? = null

    private val frameCallback = Renderer.FrameReadyForProcessingCallback { frameNotification ->
        frameNotification?.read(object : AndroidReadVideoFrameCallback() {
            override fun onVideoFrameEvent(videoFrame: AndroidVideoFrame?) {
                videoFrame?.bitmap?.prepareBitmap(selfPreviewConfiguration)?.let { bitmap ->
                    selfPreviewImageView?.drawFrameBitmap(bitmap, isFirstFrameDrawn) {
                        isFirstFrameDrawn = true
                    }
                }
            }
        })
    }

    private val microphoneFailureHandler = Handler(Looper.getMainLooper())
    private val cameraFailureHandler = Handler(Looper.getMainLooper())
    private val microphoneFailureRunnable = Runnable {
        Timber.d("Audio recording has stopped")
        currentPublishConfiguration = currentPublishConfiguration.copy(isAudioEnabled = false)
        updateMediaState()
        onMicrophoneFailure()
    }
    private val videoFailureRunnable = Runnable {
        Timber.d("Video recording is stopped")
        currentPublishConfiguration = currentPublishConfiguration.copy(isVideoEnabled = false)
        updateMediaState()
        onCameraFailure()
    }

    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private var _mediaState = ConsumableSharedFlow<PhenixMediaState>(canReplay = true)

    val onError = _onError.asSharedFlow()
    val onEvent = _onEvent.asSharedFlow()
    val mediaState = _mediaState.asSharedFlow()

    init {
        pCastExpress.getUserMedia { userMedia ->
            setUserMedia(userMedia)
            observeMediaState()
        }
    }

    fun flipCamera() {
        val facingMode = if (currentPublishConfiguration.cameraFacingMode == PhenixFacingMode.USER)
            PhenixFacingMode.ENVIRONMENT else PhenixFacingMode.USER
        setCameraFacing(facingMode)
    }

    fun setCameraFacing(facing: PhenixFacingMode) {
        updateUserMedia(currentPublishConfiguration.copy(cameraFacingMode = facing)) { status, _ ->
            if (status == RequestStatus.OK) {
                updateMediaState()
                _onEvent.tryEmit(PhenixEvent.CAMERA_FLIPPED)
            } else {
                _onError.tryEmit(PhenixError.CAMERA_FLIP_FAILED)
            }
        }
    }

    fun renderOnSurface(surfaceView: SurfaceView?) {
        Timber.d("Rendering user media on SurfaceView: ${surfaceView != null}")
        videoRenderSurface.setSurfaceHolder(surfaceView?.holder)
    }

    fun renderOnImage(imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        Timber.d("Rendering user media on ImageView: ${imageView != null}")
        selfPreviewImageView = imageView
        selfPreviewConfiguration = configuration
        userMediaStream?.mediaStream?.videoTracks?.lastOrNull()?.let { videoTrack ->
            val callback = if (selfPreviewImageView == null) null else frameCallback
            if (callback == null) isFirstFrameDrawn = false
            selfVideoRenderer?.setFrameReadyCallback(videoTrack, callback)
        }
    }

    fun setSelfAudioEnabled(enabled: Boolean) {
        Timber.d("Enabling self audio: $enabled")
        currentPublishConfiguration = currentPublishConfiguration.copy(isAudioEnabled = enabled)
        updateMediaState()
    }

    fun setSelfVideoEnabled(enabled: Boolean) {
        Timber.d("Enabling self video: $enabled")
        userMediaStream?.mediaStream?.videoTracks?.firstOrNull()?.isEnabled = enabled
        currentPublishConfiguration = currentPublishConfiguration.copy(isVideoEnabled = enabled)
        updateMediaState()
        if (enabled) {
            _onEvent.tryEmit(PhenixEvent.VIDEO_ENABLED)
            Timber.d("Self video started")
        } else {
            _onEvent.tryEmit(PhenixEvent.VIDEO_DISABLED)
            Timber.d("Self video ended")
        }
    }

    fun updateUserMedia(
        publishConfiguration: PhenixPublishConfiguration,
        onUpdated: (RequestStatus, UserMediaStream) -> Unit
    ) {
        userMediaStream?.run {
            if (currentPublishConfiguration == publishConfiguration) {
                Timber.d("User media already uses provided configuration: $publishConfiguration")
                onUpdated(RequestStatus.OK, this)
                return
            }
            currentPublishConfiguration = publishConfiguration
            val optionsToApply = getUserMediaOptions(publishConfiguration)
            val optionStatus = applyOptions(optionsToApply)
            Timber.d("Updated user media stream configuration: $optionStatus, $configuration")
            if (optionStatus != RequestStatus.OK) {
                userMediaStream?.dispose()
                userMediaStream = null
                Timber.d("Failed to update user media stream settings, requesting new user media object")
                pCastExpress.getUserMedia(optionsToApply) { status, userMedia ->
                    Timber.d("Collected new media stream from pCast: $status")
                    if (userMedia == null) {
                        _onError.tryEmit(PhenixError.RENDERING_FAILED)
                        return@getUserMedia
                    }
                    setUserMedia(userMedia)
                    onUpdated(status, userMedia)
                }
            } else {
                onUpdated(optionStatus, this)
            }
        }
    }

    fun observeVideoFrames(onFrame: ((Bitmap) -> Bitmap)? = null) {
        onVideoFrame = onFrame
    }

    fun release() {
        userMediaStream?.dispose()
        userMediaStream = null
        selfVideoRenderer?.dispose()
        selfVideoRenderer = null
        selfPreviewImageView = null
        selfPreviewConfiguration = null
        currentPublishConfiguration = currentPublishConfiguration.copy(isVideoEnabled = false)
        currentPublishConfiguration = currentPublishConfiguration.copy(isAudioEnabled = false)
        updateMediaState()
    }

    private fun setUserMedia(userMedia: UserMediaStream) {
        userMediaStream = userMedia
        val wasEnabled = userMediaStream?.mediaStream?.videoTracks?.firstOrNull()?.isEnabled ?: false
        selfVideoRenderer?.dispose()
        selfVideoRenderer = userMediaStream?.mediaStream?.createRenderer(rendererOptions)
        val status = selfVideoRenderer?.start(videoRenderSurface)
        currentPublishConfiguration = currentPublishConfiguration.copy(
            isVideoEnabled = status == RendererStartStatus.OK
        )
        renderOnImage(selfPreviewImageView, selfPreviewConfiguration)
        setSelfVideoEnabled(wasEnabled)
        observeMediaState()
    }

    private fun observeMediaState() {
        userMediaStream?.run {
            mediaStream.videoTracks.firstOrNull()?.let { videoTrack ->
                setFrameReadyCallback(videoTrack) { frameNotification ->
                    cameraFailureHandler.removeCallbacks(videoFailureRunnable)
                    cameraFailureHandler.postDelayed(videoFailureRunnable, FAILURE_TIMEOUT)
                    onVideoFrame?.let { callback ->
                        frameNotification?.read(object : AndroidReadVideoFrameCallback() {
                            override fun onVideoFrameEvent(videoFrame: AndroidVideoFrame?) {
                                videoFrame?.let { frame ->
                                    val bitmap = frame.bitmap.copy(frame.bitmap.config, frame.bitmap.isMutable)
                                    frameNotification.write(
                                        AndroidVideoFrame(
                                            callback(bitmap),
                                            frame.timestampInMicroseconds,
                                            frame.durationInMicroseconds
                                        )
                                    )
                                }
                            }
                        })
                    }
                }
            }
            mediaStream.audioTracks.firstOrNull()?.let { audioTrack ->
                setFrameReadyCallback(audioTrack) {
                    microphoneFailureHandler.removeCallbacks(microphoneFailureRunnable)
                    microphoneFailureHandler.postDelayed(microphoneFailureRunnable, FAILURE_TIMEOUT)
                }
            }
        }
    }

    private fun updateMediaState() {
        _mediaState.tryEmit(PhenixMediaState(
            currentPublishConfiguration.isVideoEnabled,
            currentPublishConfiguration.isAudioEnabled,
            currentPublishConfiguration.cameraFacingMode
        ))
    }
}
