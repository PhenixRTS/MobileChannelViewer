/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.core.common

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.ImageView
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.*
import com.phenixrts.media.video.android.AndroidVideoFrame
import com.phenixrts.pcast.*
import com.phenixrts.pcast.android.AndroidReadVideoFrameCallback
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.*
import com.phenixrts.suite.phenixcore.common.*
import com.phenixrts.suite.phenixcore.common.asFacingMode
import com.phenixrts.suite.phenixcore.common.asMemberRole
import com.phenixrts.suite.phenixcore.common.asRoomType
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.delay
import timber.log.Timber

private val userMediaOptions = UserMediaOptions().apply {
    videoOptions.capabilityConstraints[DeviceCapability.FACING_MODE] = listOf(DeviceConstraint(FacingMode.USER))
    videoOptions.capabilityConstraints[DeviceCapability.HEIGHT] = listOf(DeviceConstraint(360.0))
    videoOptions.capabilityConstraints[DeviceCapability.FRAME_RATE] = listOf(DeviceConstraint(15.0))
    audioOptions.capabilityConstraints[DeviceCapability.AUDIO_ECHO_CANCELATION_MODE] =
        listOf(DeviceConstraint(AudioEchoCancelationMode.ON))
}

internal val rendererOptions get() = RendererOptions().apply {
    audioEchoCancelationMode = AudioEchoCancelationMode.ON
}

internal fun getRoomOptions(configuration: PhenixRoomConfiguration): RoomOptions {
    var roomOptionsBuilder = RoomServiceFactory.createRoomOptionsBuilder()
        .withType(configuration.roomType.asRoomType())
    if (configuration.roomAlias.isNotBlank()) {
        roomOptionsBuilder = roomOptionsBuilder.withAlias(configuration.roomAlias).withName(configuration.roomAlias)
    } else if (configuration.roomId.isNotBlank()) {
        roomOptionsBuilder = roomOptionsBuilder.withAlias(configuration.roomId).withName(configuration.roomId)
    }
    return roomOptionsBuilder.buildRoomOptions()
}

internal fun getPublishToRoomOptions(
    roomOptions: RoomOptions,
    publishOptions: PublishOptions,
    configuration: PhenixRoomConfiguration
): PublishToRoomOptions {
    var options = RoomExpressFactory.createPublishToRoomOptionsBuilder()
        .withMemberRole(configuration.memberRole.asMemberRole())
        .withPublishOptions(publishOptions)
    options = if (configuration.roomId.isNotBlank()) {
        options.withRoomId(configuration.roomId)
    } else {
        options.withRoomOptions(roomOptions)
    }
    if (configuration.memberName.isNotBlank()) {
        options.withScreenName(configuration.memberName)
    }
    return options.buildPublishToRoomOptions()
}

internal fun getPublishToChannelOptions(
    configuration: PhenixConfiguration,
    channelConfiguration: PhenixChannelConfiguration,
    userMediaStream: UserMediaStream
): PublishToChannelOptions {
    val channelOptions = RoomServiceFactory.createChannelOptionsBuilder()
        .withName(channelConfiguration.channelAlias)
        .withAlias(channelConfiguration.channelAlias)
        .buildChannelOptions()
    var publishOptionsBuilder = PCastExpressFactory.createPublishOptionsBuilder()
        .withUserMedia(userMediaStream)
    publishOptionsBuilder = if (!configuration.publishToken.isNullOrBlank()) {
        Timber.d("Publishing with publish token: ${configuration.publishToken}")
        publishOptionsBuilder.withStreamToken(configuration.publishToken).withSkipRetryOnUnauthorized()
    } else {
        Timber.d("Publishing with capabilities")
        publishOptionsBuilder.withCapabilities(channelConfiguration.channelCapabilities.toTypedArray())
    }
    return ChannelExpressFactory.createPublishToChannelOptionsBuilder()
        .withChannelOptions(channelOptions)
        .withPublishOptions(publishOptionsBuilder.buildPublishOptions())
        .buildPublishToChannelOptions()
}

internal fun getPublishOptions(userMediaStream: UserMediaStream, publishToken: String?,
                               channelCapabilities: List<String>): PublishOptions {
    var options = PCastExpressFactory.createPublishOptionsBuilder()
        .withUserMedia(userMediaStream)
    options = if (!publishToken.isNullOrBlank()) {
        Timber.d("Publishing with publish token: $publishToken")
        options.withStreamToken(publishToken).withSkipRetryOnUnauthorized()
    } else if (channelCapabilities.isNotEmpty()) {
        Timber.d("Publishing with capabilities: $channelCapabilities")
        options.withCapabilities(channelCapabilities.toTypedArray())
    } else {
        Timber.d("Publishing with capabilities")
        options.withCapabilities(arrayOf("ld", "multi-bitrate", "prefer-h264"))
    }
    return options.buildPublishOptions()
}

internal fun getJoinRoomOptions(configuration: PhenixRoomConfiguration): JoinRoomOptions {
    var joinRoomOptionsBuilder = RoomExpressFactory.createJoinRoomOptionsBuilder()
        .withScreenName(configuration.memberName)
    if (configuration.roomAlias.isNotBlank()) {
        joinRoomOptionsBuilder = joinRoomOptionsBuilder.withRoomAlias(configuration.roomAlias)
    } else if (configuration.roomId.isNotBlank()) {
        joinRoomOptionsBuilder = joinRoomOptionsBuilder.withRoomId(configuration.roomId)
    }
    return joinRoomOptionsBuilder.buildJoinRoomOptions()
}

internal fun getCreateRoomOptions(configuration: PhenixRoomConfiguration): RoomOptions {
    var roomOptionsBuilder = RoomServiceFactory.createRoomOptionsBuilder()
        .withType(configuration.roomType.asRoomType())
    if (configuration.roomAlias.isNotBlank()) {
        roomOptionsBuilder = roomOptionsBuilder.withAlias(configuration.roomAlias).withName(configuration.roomAlias)
    } else if (configuration.roomId.isNotBlank()) {
        roomOptionsBuilder = roomOptionsBuilder.withName(configuration.roomId)
    }
    return roomOptionsBuilder.buildRoomOptions()
}

internal fun RoomExpress.joinRoom(configuration: PhenixRoomConfiguration, onJoined: (service: RoomService?) -> Unit) {
    joinRoom(getJoinRoomOptions(configuration)) { requestStatus: RequestStatus?, roomService: RoomService? ->
        Timber.d("Room join completed with status: $requestStatus")
        onJoined(roomService)
    }
}

internal fun RoomExpress.createRoom(configuration: PhenixRoomConfiguration, onCreated: (RequestStatus) -> Unit) {
    createRoom(getCreateRoomOptions(configuration)) { status, _ ->
        Timber.d("Room create completed with status: $status")
        onCreated(status)
    }
}

internal fun RoomExpress.publishInRoom(options: PublishToRoomOptions, onPublished: (ExpressPublisher?, RoomService?) -> Unit) {
    publishToRoom(options) { requestStatus: RequestStatus, service: RoomService?, publisher: ExpressPublisher? ->
        Timber.d("Media is published to room: $requestStatus, ${publisher != null}")
        onPublished(publisher, service)
    }
}

internal fun getSubscribeVideoOptions(
    rendererSurface: AndroidVideoRenderSurface,
    aspectRatioMode: AspectRatioMode,
    configuration: PhenixRoomConfiguration?
): SubscribeToMemberStreamOptions {
    val rendererOptions = rendererOptions
    rendererOptions.aspectRatioMode = aspectRatioMode
    var memberStreamOptionsBuilder = RoomExpressFactory.createSubscribeToMemberStreamOptionsBuilder()
        .withRenderer(rendererSurface)
        .withRendererOptions(rendererOptions)
    memberStreamOptionsBuilder = if (configuration?.roomVideoToken != null) {
        memberStreamOptionsBuilder.withStreamToken(configuration.roomVideoToken)
    } else {
        memberStreamOptionsBuilder.withCapabilities(arrayOf("video-only"))
    }
    return memberStreamOptionsBuilder.buildSubscribeToMemberStreamOptions()
}

internal fun getSubscribeAudioOptions(configuration: PhenixRoomConfiguration?): SubscribeToMemberStreamOptions {
    var memberStreamOptionsBuilder = RoomExpressFactory.createSubscribeToMemberStreamOptionsBuilder()
        .withAudioOnlyRenderer()
        .withRendererOptions(rendererOptions)
    memberStreamOptionsBuilder = if (configuration?.roomAudioToken != null) {
        memberStreamOptionsBuilder.withStreamToken(configuration.roomAudioToken)
    } else {
        memberStreamOptionsBuilder.withCapabilities(arrayOf("audio-only"))
    }
    return memberStreamOptionsBuilder.buildSubscribeToMemberStreamOptions()
}

internal fun PCastExpress.getUserMedia(onMediaCollected: (userMedia: UserMediaStream) -> Unit) {
    getUserMedia(userMediaOptions) { status, stream ->
        Timber.d("Collecting media stream from pCast: $status")
        onMediaCollected(stream)
    }
}

internal fun getUserMediaOptions(configuration: PhenixPublishConfiguration): UserMediaOptions = UserMediaOptions().apply {
    if (configuration.cameraFacingMode != PhenixFacingMode.UNDEFINED) {
        videoOptions.capabilityConstraints[DeviceCapability.FACING_MODE] =
            listOf(DeviceConstraint(configuration.cameraFacingMode.asFacingMode()))
    }
    videoOptions.capabilityConstraints[DeviceCapability.HEIGHT] =
        listOf(DeviceConstraint(360.0))
    videoOptions.capabilityConstraints[DeviceCapability.FRAME_RATE] =
        listOf(DeviceConstraint(configuration.cameraFps))
    videoOptions.enabled = configuration.isVideoEnabled
    audioOptions.capabilityConstraints[DeviceCapability.AUDIO_ECHO_CANCELATION_MODE] =
        listOf(DeviceConstraint(configuration.echoCancellationMode.asAudioEchoCancelationMode()))
    audioOptions.enabled = configuration.isAudioEnabled
    Timber.d("Creating user media options from configuration: $configuration")
}

internal fun onVideoFrameCallback(
    configuration: PhenixFrameReadyConfiguration?,
    onBitmapPrepared: (Bitmap) -> Unit
) = Renderer.FrameReadyForProcessingCallback { frameNotification ->
    frameNotification?.read(object : AndroidReadVideoFrameCallback() {
        override fun onVideoFrameEvent(videoFrame: AndroidVideoFrame?) {
            videoFrame?.bitmap?.let { bitmap ->
                onBitmapPrepared(bitmap.prepareBitmap(configuration))
            }
        }
    })
}

internal fun ImageView.drawFrameBitmap(bitmap: Bitmap, delay: Boolean, onDrawn: () -> Unit) {
    try {
        launchMain {
            if (bitmap.isRecycled) return@launchMain
            if (delay) delay(THUMBNAIL_DRAW_DELAY)
            setImageBitmap(bitmap.copy(bitmap.config, bitmap.isMutable))
            onDrawn()
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to draw bitmap")
    }
}

internal fun Bitmap.prepareBitmap(configuration: PhenixFrameReadyConfiguration?): Bitmap {
    if (configuration == null) return this
    return try {
        val newWidth = configuration.width.takeIf { it < width } ?: width
        val newHeight = configuration.height.takeIf { it < height } ?: height
        val matrix = Matrix()
        if (newWidth > 0 && newHeight > 0) {
            val scaleWidth = newWidth / width.toFloat()
            val scaleHeight = newHeight / height.toFloat()
            matrix.postScale(scaleWidth, scaleHeight)
        }
        matrix.postRotate(configuration.rotation)
        Bitmap.createBitmap(this, 0, 0, newWidth, newHeight, matrix, true)
    } catch (e: Exception) {
        Timber.e(e, "Failed to prepare bitmap for: $configuration, $width, $height")
        this
    }
}

internal fun Renderer?.isSeekable() = try {
    this?.isSeekable ?: false
} catch (e: Exception) {
    false
}
