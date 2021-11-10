/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.room.models

import android.view.SurfaceView
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.ExpressSubscriber
import com.phenixrts.express.RoomExpress
import com.phenixrts.express.SubscribeToMemberStreamOptions
import com.phenixrts.media.audio.android.AndroidAudioFrame
import com.phenixrts.pcast.*
import com.phenixrts.pcast.android.AndroidReadAudioFrameCallback
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.*
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.core.common.getSubscribeAudioOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.getSubscribeVideoOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.rendererOptions
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.log10
import kotlin.properties.Delegates

private const val READ_TIMEOUT_DELAY = 200
private const val DEFAULT_BANDWIDTH_LIMIT = 350_000L
private const val INITIAL_AUDIO_LEVEL = 1f

internal data class PhenixCoreMember(
    var member: Member,
    var isSelf: Boolean
) {

    private val videoRenderSurface by lazy { AndroidVideoRenderSurface() }
    private var mediaStreamDisposable: Disposable? = null
    private var videoStateDisposable: Disposable? = null
    private var audioStateDisposable: Disposable? = null
    private var bandwidthDisposable: Disposable? = null
    private var videoSubscriber: ExpressSubscriber? = null
    private var audioSubscriber: ExpressSubscriber? = null
    private var audioRenderer: Renderer? = null
    private var videoRenderer: Renderer? = null
    private var currentStreamUri = ""
    private var isSubscribed = false
    private var readDelay = System.currentTimeMillis()
    private var audioBuffer = arrayListOf<Double>()

    private val _onUpdated = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _onError = MutableSharedFlow<PhenixError>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val onUpdated: SharedFlow<Unit> = _onUpdated
    val onError: SharedFlow<PhenixError> = _onError

    val memberId get() = member.sessionId ?: ""
    val isDisposable get() = !isSelf && !isModerator
    val hasRaisedHand get() = member.observableState.value == MemberState.HAND_RAISED
    val isModerator get() = memberRole == MemberRole.MODERATOR

    var memberRole: MemberRole by Delegates.observable(member.observableRole.value) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member role changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var memberState: MemberState by Delegates.observable(member.observableState.value) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member state changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var memberName: String by Delegates.observable(member.observableScreenName.value) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member name changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var audioLevel by Delegates.observable(INITIAL_AUDIO_LEVEL) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member audio level changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var isSelected by Delegates.observable(false) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member isSelected changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var isAudioEnabled by Delegates.observable(false) { _, oldValue, newValue ->
        if (newValue) {
            audioRenderer?.unmuteAudio()
        } else {
            audioRenderer?.muteAudio()
        }
        if (oldValue != newValue) {
            Timber.d("Member audio state changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var isVideoEnabled by Delegates.observable(false) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member video state changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var isSpeaking by Delegates.observable(false) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member speaking state changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
        private set

    private fun updateSpeakingState(decibel: Double) {
        isSpeaking = PhenixCoreAudioLevel.getVolume(decibel).isSpeaking()
    }

    private fun observeAudio() {
        audioSubscriber?.audioTracks?.lastOrNull()?.let { audioTrack ->
            audioRenderer?.setFrameReadyCallback(audioTrack) { frame ->
                readFrame(frame)
            }
        }
    }

    private fun readFrame(frame: FrameNotification) = synchronized(audioBuffer) {
        frame.read(object: AndroidReadAudioFrameCallback() {
            override fun onAudioFrameEvent(audioFrame: AndroidAudioFrame?) {
                audioFrame?.audioSamples?.let { samples ->
                    val now = System.currentTimeMillis()
                    audioBuffer.add(samples.map { abs(it.toInt()) }.average())
                    if (now - readDelay > READ_TIMEOUT_DELAY) {
                        val decibel = 20.0 * log10(audioBuffer.average() / Short.MAX_VALUE)
                        updateSpeakingState(decibel)
                        readDelay = now
                        audioBuffer.clear()
                    }
                    if (audioLevel < 1f) {
                        samples.forEachIndexed { index, sample ->
                            samples[index] = (sample * audioLevel).toInt().toShort()
                        }
                        frame.write(audioFrame)
                    }
                }
            }
        })
    }

    private fun limitBandwidth() {
        videoSubscriber?.videoTracks?.getOrNull(0)?.limitBandwidth(DEFAULT_BANDWIDTH_LIMIT)?.run {
            Timber.d("Bandwidth limited: ${asString()}")
            bandwidthDisposable = this
        }
    }

    private suspend fun subscribeToStream(roomExpress: RoomExpress, stream: Stream,
                                          options: SubscribeToMemberStreamOptions, isVideo: Boolean) = suspendCancellableCoroutine<Unit> { continuation ->
        roomExpress.subscribeToMemberStream(stream, options) { status, expressSubscriber, mediaRenderer ->
            Timber.d("Subscribed to member media: $status ${asString()}")
            if (status != RequestStatus.OK) {
                if (continuation.isActive) continuation.resume(Unit)
                return@subscribeToMemberStream
            }
            if (isVideo) {
                videoSubscriber = expressSubscriber
                videoRenderer = mediaRenderer
                val state = videoRenderer?.start(videoRenderSurface)
                Timber.d("Started video renderer: $state, ${asString()}")
                isVideoEnabled = stream.observableVideoState.value == TrackState.ENABLED
                limitBandwidth()
            } else {
                audioSubscriber = expressSubscriber
                audioRenderer = mediaRenderer
                isAudioEnabled = stream.observableAudioState.value == TrackState.ENABLED
                observeAudio()
            }
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    fun isThisMember(sessionId: String?) = member.sessionId == sessionId

    fun renderOnSurface(surfaceView: SurfaceView?) {
        Timber.d("Renderer ${if (surfaceView == null) "disabled" else "enabled"} on surface view for: ${asString()}")
        videoRenderSurface.setSurfaceHolder(surfaceView?.holder)
    }

    fun createSelfRenderer(userMediaStream: UserMediaStream?) {
        if (videoRenderer == null && isSelf) {
            videoRenderer = userMediaStream?.mediaStream?.createRenderer(rendererOptions)
            val status = videoRenderer?.start(videoRenderSurface)
            val rendererStarted = status == RendererStartStatus.OK
            isVideoEnabled = rendererStarted
            isAudioEnabled = rendererStarted
            Timber.d("Started self renderer: $status, ${asString()}")
        }
    }

    fun subscribeToMemberMedia(roomExpress: RoomExpress) {
        if (isSubscribed) return
        isSubscribed = true
        Timber.d("Subscribing to member media: ${asString()}")
        member.observableStreams.subscribe { streams ->
            streams.lastOrNull()?.let { stream ->
                launchMain {
                    Timber.d("Member stream count changed: ${streams.size}, re-subscribing: ${stream.streamUri != currentStreamUri}")
                    if (stream.streamUri == currentStreamUri) return@launchMain
                    currentStreamUri = stream.streamUri
                    if (!isSelf || isSelected) {
                        Timber.d("Subscribing to member stream: $currentStreamUri ${asString()}")
                        val aspectRatioMode = AspectRatioMode.FILL
                        val videoOptions = getSubscribeVideoOptions(videoRenderSurface, aspectRatioMode)
                        val audioOptions = getSubscribeAudioOptions()
                        subscribeToStream(roomExpress, stream, videoOptions, true)
                        subscribeToStream(roomExpress, stream, audioOptions, false)
                    }
                    stream.observableVideoState.subscribe { trackState ->
                        Timber.d("Member video state changed: $trackState ${asString()}")
                        isVideoEnabled = stream.observableVideoState.value == TrackState.ENABLED
                    }.run {
                        videoStateDisposable?.dispose()
                        videoStateDisposable = this
                    }
                    stream.observableAudioState.subscribe { trackState ->
                        Timber.d("Member audio state changed: $trackState ${asString()}")
                        isAudioEnabled = stream.observableAudioState.value == TrackState.ENABLED
                    }.run {
                        audioStateDisposable?.dispose()
                        audioStateDisposable = this
                    }
                }
            }
        }.run {
            mediaStreamDisposable?.dispose()
            mediaStreamDisposable = this
        }
    }

    fun dispose() = try {
        mediaStreamDisposable?.dispose()
        videoStateDisposable?.dispose()
        audioStateDisposable?.dispose()
        bandwidthDisposable?.dispose()
        isSubscribed = false
        isSelected = false
        currentStreamUri = ""
        videoRenderSurface.setSurfaceHolder(null)
        if (!isSelf) {
            videoSubscriber?.stop()
            videoSubscriber?.dispose()
            videoSubscriber = null
            audioSubscriber?.stop()
            audioSubscriber?.dispose()
            audioSubscriber = null

            videoRenderer?.dispose()
            videoRenderer = null
            audioRenderer?.dispose()
            audioRenderer = null
        }
        Timber.d("Room member disposed: ${asString()}")
    } catch (e: Exception) {
        Timber.d("Failed to dispose room member: ${asString()}")
    }

    private fun asString() = this@PhenixCoreMember.toString()

    override fun toString(): String {
        return "{\"name\":\"${member.observableScreenName.value}\"," +
                "\"role\":\"${member.observableRole.value}\"," +
                "\"state\":\"${member.observableState.value}\"," +
                "\"hasRenderer\":\"${videoRenderer != null}\"," +
                "\"isSubscribed\":\"${isSubscribed}\"," +
                "\"isAudioEnabled\":\"${isAudioEnabled}\"," +
                "\"isVideoEnabled\":\"${isVideoEnabled}\"," +
                "\"isSelf\":\"${isSelf}\"," +
                "\"isSelected\":\"${isSelected}\"," +
                "\"hasRaisedHand\":\"${hasRaisedHand}\"," +
                "\"hasAudioRenderer\":\"${audioRenderer != null}\"," +
                "\"hasVideoRenderer\":\"${videoRenderer != null}\"," +
                "\"isModerator\":\"${isModerator}\"}"
    }
}
