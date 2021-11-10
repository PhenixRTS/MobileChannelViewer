/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.core.common

import com.phenixrts.common.RequestStatus
import com.phenixrts.express.*
import com.phenixrts.pcast.*
import com.phenixrts.pcast.android.AndroidVideoRenderSurface
import com.phenixrts.room.*
import com.phenixrts.suite.phenixcore.repositories.models.PhenixRoomConfiguration
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

internal fun getRoomOptions(roomAlias: String, roomType: RoomType): RoomOptions = RoomServiceFactory.createRoomOptionsBuilder()
    .withAlias(roomAlias)
    .withName(roomAlias)
    .withType(roomType)
    .buildRoomOptions()

internal fun getPublishToRoomOptions(roomOptions: RoomOptions, publishOptions: PublishOptions, role: MemberRole, name: String? = null): PublishToRoomOptions {
    val options = RoomExpressFactory.createPublishToRoomOptionsBuilder()
        .withMemberRole(role)
        .withRoomOptions(roomOptions)
        .withPublishOptions(publishOptions)
    if (name != null) {
        options.withScreenName(name)
    }
    return options.buildPublishToRoomOptions()
}

internal fun getPublishOptions(userMediaStream: UserMediaStream): PublishOptions = PCastExpressFactory.createPublishOptionsBuilder()
    .withUserMedia(userMediaStream)
    .withCapabilities(arrayOf("ld", "multi-bitrate", "prefer-h264"))
    .buildPublishOptions()

internal fun getJoinRoomOptions(configuration: PhenixRoomConfiguration): JoinRoomOptions =
    RoomExpressFactory.createJoinRoomOptionsBuilder()
        .withRoomAlias(configuration.roomAlias)
        .withScreenName(configuration.memberName)
        .buildJoinRoomOptions()

internal fun RoomExpress.joinRoom(configuration: PhenixRoomConfiguration, onJoined: (service: RoomService?) -> Unit) {
    joinRoom(getJoinRoomOptions(configuration)) { requestStatus: RequestStatus?, roomService: RoomService? ->
        Timber.d("Room join completed with status: $requestStatus")
        onJoined(roomService)
    }
}

internal fun RoomExpress.publishToRoom(options: PublishToRoomOptions, onPublished: (publisher: ExpressPublisher?) -> Unit) {
    publishToRoom(options) { requestStatus: RequestStatus, _: RoomService?, publisher: ExpressPublisher? ->
        Timber.d("Media is published to room: $requestStatus")
        onPublished(publisher)
    }
}

internal fun getSubscribeVideoOptions(rendererSurface: AndroidVideoRenderSurface, aspectRatioMode: AspectRatioMode): SubscribeToMemberStreamOptions {
    val rendererOptions = rendererOptions
    rendererOptions.aspectRatioMode = aspectRatioMode
    return RoomExpressFactory.createSubscribeToMemberStreamOptionsBuilder()
        .withRenderer(rendererSurface)
        .withRendererOptions(rendererOptions)
        .withCapabilities(arrayOf("video-only"))
        .buildSubscribeToMemberStreamOptions()
}

internal fun getSubscribeAudioOptions(): SubscribeToMemberStreamOptions =
    RoomExpressFactory.createSubscribeToMemberStreamOptionsBuilder()
        .withCapabilities(arrayOf("audio-only"))
        .withAudioOnlyRenderer()
        .withRendererOptions(rendererOptions)
        .buildSubscribeToMemberStreamOptions()

internal fun PCastExpress.getUserMedia(onMediaCollected: (userMedia: UserMediaStream) -> Unit) {
    getUserMedia(userMediaOptions) { status, stream ->
        Timber.d("Collecting media stream from pCast: $status")
        onMediaCollected(stream)
    }
}
