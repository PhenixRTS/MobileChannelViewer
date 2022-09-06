/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.common

import com.phenixrts.chat.ChatMessage
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.RoomExpress
import com.phenixrts.pcast.AudioEchoCancelationMode
import com.phenixrts.pcast.FacingMode
import com.phenixrts.room.Member
import com.phenixrts.room.MemberRole
import com.phenixrts.room.MemberState
import com.phenixrts.room.RoomType
import com.phenixrts.suite.phenixcore.repositories.channel.models.PhenixCoreChannel
import com.phenixrts.suite.phenixcore.repositories.models.*
import com.phenixrts.suite.phenixcore.repositories.room.models.PhenixCoreMember
import com.phenixrts.suite.phenixcore.repositories.stream.models.PhenixCoreStream
import timber.log.Timber
import kotlin.properties.Delegates

internal fun Set<PhenixCoreChannel>.asPhenixChannels() = map { channel ->
    PhenixChannel(
        alias = channel.channelAlias,
        isAudioEnabled = channel.isAudioEnabled,
        isVideoEnabled = channel.isVideoEnabled,
        isSelected = channel.isSelected,
        timeShiftHead = channel.timeShiftHead,
        timeShiftState = channel.timeShiftState,
        channelState = channel.channelState,
    )
}

internal fun Set<PhenixCoreStream>.asPhenixStreams() = map { stream ->
    PhenixStream(
        id = stream.streamID,
        isAudioEnabled = stream.isAudioEnabled,
        isVideoEnabled = stream.isVideoEnabled,
        isSelected = stream.isSelected,
        timeShiftHead = stream.timeShiftHead,
        timeShiftState = stream.timeShiftState,
        streamState = stream.streamState,
    )
}

internal fun Set<PhenixCoreMember>.asPhenixMembers() = map { member ->
    member.asPhenixMember()
}

internal fun PhenixCoreMember.asPhenixMember() = PhenixMember(
    id = memberId,
    name = memberName,
    role = memberRole.asPhenixMemberRole(),
    isAudioEnabled = isAudioEnabled,
    isVideoEnabled = isVideoEnabled,
    volume = volume,
    hasRaisedHand = hasRaisedHand,
    isSelected = isSelected,
    isSelf = isSelf,
    handRaiseTimestamp = handRaiseTimestamp,
    connectionState = connectionState
)

internal fun PhenixMemberRole.asMemberRole() = when (this) {
    PhenixMemberRole.PARTICIPANT -> MemberRole.PARTICIPANT
    PhenixMemberRole.MODERATOR -> MemberRole.MODERATOR
    PhenixMemberRole.PRESENTER -> MemberRole.PRESENTER
    PhenixMemberRole.AUDIENCE -> MemberRole.AUDIENCE
}

internal fun PhenixMemberState.asMemberState() = when (this) {
    PhenixMemberState.ACTIVE -> MemberState.ACTIVE
    PhenixMemberState.PASSIVE -> MemberState.PASSIVE
    PhenixMemberState.HAND_RAISED -> MemberState.HAND_RAISED
    PhenixMemberState.INACTIVE -> MemberState.INACTIVE
    PhenixMemberState.OFFLINE -> MemberState.OFFLINE
}

internal fun PhenixRoomType.asRoomType() = when (this) {
    PhenixRoomType.DIRECT_CHAT -> RoomType.DIRECT_CHAT
    PhenixRoomType.MULTI_PARTY_CHAT -> RoomType.MULTI_PARTY_CHAT
    PhenixRoomType.MODERATED_CHAT -> RoomType.MODERATED_CHAT
    PhenixRoomType.TOWN_HALL -> RoomType.TOWN_HALL
    PhenixRoomType.CHANNEL -> RoomType.CHANNEL
}

internal fun MemberRole.asPhenixMemberRole() = when (this) {
    MemberRole.PARTICIPANT -> PhenixMemberRole.PARTICIPANT
    MemberRole.MODERATOR -> PhenixMemberRole.MODERATOR
    MemberRole.PRESENTER -> PhenixMemberRole.PRESENTER
    MemberRole.AUDIENCE -> PhenixMemberRole.AUDIENCE
}

internal fun PhenixFacingMode.asFacingMode() = when (this) {
    PhenixFacingMode.AUTOMATIC -> FacingMode.AUTOMATIC
    PhenixFacingMode.UNDEFINED -> FacingMode.UNDEFINED
    PhenixFacingMode.USER -> FacingMode.USER
    PhenixFacingMode.ENVIRONMENT -> FacingMode.ENVIRONMENT
}

internal fun PhenixAudioEchoCancelationMode.asAudioEchoCancelationMode() = when (this) {
    PhenixAudioEchoCancelationMode.AUTOMATIC -> AudioEchoCancelationMode.AUTOMATIC
    PhenixAudioEchoCancelationMode.ON -> AudioEchoCancelationMode.ON
    PhenixAudioEchoCancelationMode.OFF -> AudioEchoCancelationMode.OFF
}

internal fun Member.mapRoomMember(
    members: Set<PhenixCoreMember>?,
    selfSessionId: String?,
    express: RoomExpress,
    configuration: PhenixRoomConfiguration?
) = members?.find { it.isThisMember(this@mapRoomMember.sessionId) }?.apply {
    member = this@mapRoomMember
    isSelf = this@mapRoomMember.sessionId == selfSessionId
    roomExpress = express
    roomConfiguration = configuration
} ?: PhenixCoreMember(this, this@mapRoomMember.sessionId == selfSessionId, express, configuration)

internal fun PhenixCoreMember.updateMember(
    role: MemberRole,
    state: MemberState,
    name: String,
    onError: () -> Unit,
    onUpdated: () -> Unit
) {
    member.observableRole.value = role
    member.observableState.value = state
    member.observableScreenName.value = name
    member.commitChanges { requestStatus, message ->
        Timber.d("Member role changed: $role $requestStatus $message")
        if (requestStatus == RequestStatus.OK) {
            memberRole = role
            memberState = state
            memberName = name
            onUpdated()
        } else {
            onError()
        }
    }
}

internal fun ChatMessage.asPhenixMessage(alias: String) = PhenixMessage(
    alias = alias,
    messageId = messageId,
    messageDate = observableTimeStamp.value.time,
    messageMimeType = observableMimeType.value,
    message = observableMessage.value,
    memberId = observableFrom.value.sessionId,
    memberRole = observableFrom.value.observableMemberRole.value.asPhenixMemberRole(),
    memberName = observableFrom.value.observableScreenName.value
)

internal fun Set<PhenixMessage>.asCopy() = map { it.copy() }

internal fun <T> observableUnique(initialValue: T, onChange: (newValue: T) -> Unit) =
    Delegates.observable(initialValue) { _, oldValue, newValue ->
        if (oldValue != newValue) onChange(newValue)
    }
