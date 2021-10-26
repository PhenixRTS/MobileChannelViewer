/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.common

import com.phenixrts.chat.ChatMessage
import com.phenixrts.common.RequestStatus
import com.phenixrts.room.Member
import com.phenixrts.room.MemberRole
import com.phenixrts.room.MemberState
import com.phenixrts.suite.phenixcore.deeplink.models.PhenixDeepLinkConfiguration
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannel
import com.phenixrts.suite.phenixcore.repositories.channels.models.PhenixCoreChannel
import com.phenixrts.suite.phenixcore.repositories.models.PhenixConfiguration
import com.phenixrts.suite.phenixcore.repositories.models.PhenixMember
import com.phenixrts.suite.phenixcore.repositories.models.PhenixMessage
import com.phenixrts.suite.phenixcore.repositories.room.models.PhenixCoreMember
import timber.log.Timber

internal fun List<PhenixCoreChannel>.asPhenixChannels() = map { channel ->
    PhenixChannel(
        alias = channel.alias,
        isAudioEnabled = channel.isAudioEnabled,
        isVideoEnabled = channel.isVideoEnabled,
        isSelected = channel.isSelected,
        timeShiftHead = channel.timeShiftHead,
        timeShiftState = channel.timeShiftState,
        channelState = channel.channelState
    )
}

internal fun List<PhenixCoreMember>.asPhenixMembers() = map { member ->
    member.asPhenixMember()
}

internal fun PhenixCoreMember.asPhenixMember() = PhenixMember(
    id = memberId,
    name = memberName,
    role = memberRole,
    isAudioEnabled = isAudioEnabled,
    isVideoEnabled = isVideoEnabled,
    isSpeaking = isSpeaking,
    hasRaisedHand = hasRaisedHand,
    isSelected = isSelected,
    isSelf = isSelf
)

internal fun Member.mapRoomMember(members: List<PhenixCoreMember>?, selfSessionId: String?): PhenixCoreMember =
    members?.find { it.isThisMember(this@mapRoomMember.sessionId) }?.apply {
        member = this@mapRoomMember
        isSelf = this@mapRoomMember.sessionId == selfSessionId
    } ?: PhenixCoreMember(this, this@mapRoomMember.sessionId == selfSessionId)

internal fun PhenixCoreMember.updateMember(
    role: MemberRole,
    state: MemberState,
    name: String,
    onError: () -> Unit
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
        } else {
            onError()
        }
    }
}

internal fun PhenixDeepLinkConfiguration.asPhenixConfiguration() = PhenixConfiguration(
    backend = backend,
    uri = uri,
    authToken = edgeToken,
    streamTokens = streamTokens,
    channels = channels,
    streams = streams,
    mimeTypes = mimeTypes
)

internal fun ChatMessage.asPhenixMessage() = PhenixMessage(
    messageId = messageId,
    messageDate = observableTimeStamp.value.time,
    messageMimeType = observableMimeType.value,
    message = observableMessage.value,
    memberId = observableFrom.value.sessionId,
    memberRole = observableFrom.value.observableMemberRole.value,
    memberName = observableFrom.value.observableScreenName.value
)

internal fun List<PhenixMessage>.asCopy() = map { it.copy() }
