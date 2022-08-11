/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.room.models

import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.ImageView
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
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.core.common.*
import com.phenixrts.suite.phenixcore.repositories.core.common.getSubscribeAudioOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.getSubscribeVideoOptions
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixcore.repositories.models.PhenixFrameReadyConfiguration
import com.phenixrts.suite.phenixcore.repositories.models.PhenixMemberConnectionState
import com.phenixrts.suite.phenixcore.repositories.models.PhenixRoomConfiguration
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.log10
import kotlin.properties.Delegates

private const val READ_TIMEOUT_DELAY = 200
private const val DEFAULT_BANDWIDTH_LIMIT = 350_000L
private const val INITIAL_AUDIO_LEVEL = 1f
private const val STREAM_SUBSCRIPTION_TIMEOUT = 1000 * 10L

internal data class PhenixCoreMember(
    var member: Member,
    var isSelf: Boolean,
    var roomExpress: RoomExpress,
    var roomConfiguration: PhenixRoomConfiguration?
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
    private var observingStreams = false
    private var isFirstFrameDrawn = false
    private var canRenderVideo = true
    private var readDelay = System.currentTimeMillis()
    private var audioBuffer = arrayListOf<Double>()
    private var streamImageView: ImageView? = null
    private var frameReadyConfiguration: PhenixFrameReadyConfiguration? = null
    private val memberStreams = mutableListOf<PhenixCoreMemberStream>()

    private val streamSubscriptionHandler = Handler(Looper.getMainLooper())
    private val streamSubscriptionRunner = Runnable {
        launchMain {
            Timber.d("Retrying stream subscription for: ${asString()}")
            subscribeToMemberStream()
        }
    }

    private val _onUpdated = ConsumableSharedFlow<Unit>()
    private val _onError = ConsumableSharedFlow<PhenixError>()

    val onUpdated = _onUpdated.asSharedFlow()
    val onError = _onError.asSharedFlow()

    @Suppress("MemberVisibilityCanBePrivate")
    val isModerator get() = memberRole == MemberRole.MODERATOR
    val memberId get() = member.sessionId ?: ""
    val isDisposable get() = !isSelf && !isModerator
    val hasRaisedHand get() = member.observableState.value == MemberState.HAND_RAISED
    val isVideoRendering get() = videoRenderer != null
    var handRaiseTimestamp: Long = 0
        private set

    var connectionState: PhenixMemberConnectionState by Delegates.observable(PhenixMemberConnectionState.PENDING) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member connection state changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var memberRole: MemberRole by Delegates.observable(member.observableRole.value) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member role changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }
    var memberState: MemberState by Delegates.observable(member.observableState.value) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member state changed: $newValue for: ${asString()}")
            handRaiseTimestamp = if (hasRaisedHand) member.observableLastUpdate.value.time else 0
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
    var volume by Delegates.observable(0) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            Timber.d("Member volume changed: $newValue for: ${asString()}")
            _onUpdated.tryEmit(Unit)
        }
    }

    fun isThisMember(sessionId: String?) = member.sessionId == sessionId

    fun renderOnSurface(surfaceView: SurfaceView?) {
        if (isSelf) return
        Timber.d("Renderer ${if (surfaceView == null) "disabled" else "enabled"} on surface view for: ${asString()}")
        videoRenderSurface.setSurfaceHolder(surfaceView?.holder)
    }

    fun renderOnImage(imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        if (isSelf) return
        Timber.d("Renderer ${if (imageView == null) "disabled" else "enabled"} on image view for: ${asString()}")
        streamImageView = imageView
        frameReadyConfiguration = configuration
        videoSubscriber?.videoTracks?.lastOrNull()?.let { videoTrack ->
            val callback = if (streamImageView == null) null else onVideoFrameCallback(configuration) { bitmap ->
                streamImageView?.drawFrameBitmap(bitmap, isFirstFrameDrawn) {
                    isFirstFrameDrawn = true
                }
            }
            if (callback == null) isFirstFrameDrawn = false
            videoRenderer?.setFrameReadyCallback(videoTrack, callback)
        }
    }

    fun subscribeToMemberMedia(canRender: Boolean) {
        if (observingStreams) return
        canRenderVideo = canRender
        observingStreams = true
        connectionState = PhenixMemberConnectionState.PENDING
        Timber.d("Subscribing to member media: ${asString()}")
        member.observableStreams.subscribe { streams ->
            memberStreams.clear()
            memberStreams.addAll(streams.map { PhenixCoreMemberStream(it) })
            subscribeToMemberStream()
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
        observingStreams = false
        isSelected = false
        isFirstFrameDrawn = false
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

    private fun subscribeToMemberStream() {
        streamSubscriptionHandler.removeCallbacks(streamSubscriptionRunner)
        if (memberStreams.isEmpty()) {
            connectionState = PhenixMemberConnectionState.REMOVED
            return
        }
        connectionState = PhenixMemberConnectionState.PENDING
        memberStreams.first().let { memberStream ->
            launchMain {
                val stream = memberStream.stream
                Timber.d("Member stream count changed: ${memberStreams.size}, re-subscribing: ${!memberStream.isSubscribed}")
                if (memberStream.isSubscribed) return@launchMain
                if (!isSelf) {
                    Timber.d("Subscribing to member stream: ${stream.streamUri} ${asString()} with: $roomConfiguration")
                    val aspectRatioMode = AspectRatioMode.FILL
                    val videoOptions = getSubscribeVideoOptions(videoRenderSurface, aspectRatioMode, roomConfiguration)
                    val audioOptions = getSubscribeAudioOptions(roomConfiguration)
                    var subscribedVideo = false
                    if (canRenderVideo) {
                        subscribedVideo = subscribeToStream(roomExpress, stream, videoOptions, true)
                    }
                    val subscribedAudio = subscribeToStream(roomExpress, stream, audioOptions, false)
                    connectionState = if (subscribedVideo || subscribedAudio)
                        PhenixMemberConnectionState.ACTIVE else PhenixMemberConnectionState.AWAY
                    memberStreams.first {
                        it.stream.streamUri == stream.streamUri
                    }.isSubscribed = subscribedAudio || subscribedVideo
                    if (!subscribedAudio && !subscribedVideo) {
                        memberStreams.remove(memberStream)
                        streamSubscriptionHandler.postDelayed(streamSubscriptionRunner, STREAM_SUBSCRIPTION_TIMEOUT)
                        return@launchMain
                    }
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
                observeDataQuality()
            }
        }
    }

    private fun observeAudio() {
        audioSubscriber?.audioTracks?.lastOrNull()?.let { audioTrack ->
            audioRenderer?.setFrameReadyCallback(audioTrack) { frame ->
                synchronized(audioBuffer) {
                    frame.read(object: AndroidReadAudioFrameCallback() {
                        override fun onAudioFrameEvent(audioFrame: AndroidAudioFrame?) {
                            audioFrame?.audioSamples?.let { samples ->
                                val now = System.currentTimeMillis()
                                audioBuffer.add(samples.map { abs(it.toInt()) }.average())
                                if (now - readDelay > READ_TIMEOUT_DELAY) {
                                    val decibel = 20.0 * log10(audioBuffer.average() / Short.MAX_VALUE)
                                    volume = PhenixCoreAudioLevel.getVolume(decibel).ordinal
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
            }
        }
    }

    private fun limitBandwidth() {
        videoSubscriber?.videoTracks?.getOrNull(0)?.limitBandwidth(DEFAULT_BANDWIDTH_LIMIT)?.run {
            Timber.d("Bandwidth limited: ${asString()}")
            bandwidthDisposable = this
        }
    }

    private suspend fun subscribeToStream(
        roomExpress: RoomExpress,
        stream: Stream,
        options: SubscribeToMemberStreamOptions,
        isVideo: Boolean
    ) = suspendCancellableCoroutine { continuation ->
        roomExpress.subscribeToMemberStream(stream, options) { status, expressSubscriber, mediaRenderer ->
            Timber.d("Subscribed to member media: $status ${asString()}")
            if (status != RequestStatus.OK) {
                if (continuation.isActive) continuation.resume(false)
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
            if (continuation.isActive) continuation.resume(true)
        }
    }

    private fun observeDataQuality() {
        val rendererToObserve = if (videoRenderer != null) videoRenderer else audioRenderer
        rendererToObserve?.setDataQualityChangedCallback { _, status, _ ->
            if (status == DataQualityStatus.ALL) {
                connectionState = PhenixMemberConnectionState.ACTIVE
                streamSubscriptionHandler.removeCallbacks(streamSubscriptionRunner)
            } else if (status == DataQualityStatus.NO_DATA) {
                Timber.d("Renderer data lost for: ${asString()}")
                connectionState = PhenixMemberConnectionState.AWAY
                streamSubscriptionHandler.postDelayed(streamSubscriptionRunner, STREAM_SUBSCRIPTION_TIMEOUT)
            }
        }
    }

    private fun asString() = this@PhenixCoreMember.toString()

    override fun toString(): String {
        return "{\"name\":\"${member.observableScreenName.value}\"," +
                "\"role\":\"${member.observableRole.value}\"," +
                "\"state\":\"${member.observableState.value}\"," +
                "\"hasRenderer\":\"${videoRenderer != null}\"," +
                "\"isAudioEnabled\":\"${isAudioEnabled}\"," +
                "\"isVideoEnabled\":\"${isVideoEnabled}\"," +
                "\"canRenderVideo\":\"${canRenderVideo}\"," +
                "\"isVideoRendering\":\"${isVideoRendering}\"," +
                "\"isSelf\":\"${isSelf}\"," +
                "\"isSelected\":\"${isSelected}\"," +
                "\"hasRaisedHand\":\"${hasRaisedHand}\"," +
                "\"hasAudioRenderer\":\"${audioRenderer != null}\"," +
                "\"hasVideoRenderer\":\"${videoRenderer != null}\"," +
                "\"handRaiseTimestamp\":\"${handRaiseTimestamp}\"," +
                "\"connectionState\":\"${connectionState}\"," +
                "\"isModerator\":\"${isModerator}\"}"
    }
}
