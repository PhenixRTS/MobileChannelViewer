/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.phenixrts.suite.phenixcore.repositories.models

import com.phenixrts.suite.phenixcore.repositories.room.models.PhenixCoreAudioLevel

data class PhenixMember(
    val id: String,
    val name: String,
    val role: PhenixMemberRole,
    val volume: Int,
    val isAudioEnabled: Boolean,
    val isVideoEnabled: Boolean,
    val isSelected: Boolean,
    val isSelf: Boolean,
    val hasRaisedHand: Boolean,
    val handRaiseTimestamp: Long,
    val connectionState: PhenixMemberConnectionState
) {
    val isModerator = role == PhenixMemberRole.MODERATOR

    val isSpeaking = volume > PhenixCoreAudioLevel.VOLUME_1.ordinal

    override fun toString(): String {
        return "PhenixMember(id='$id', name='$name', role=$role, volume=$volume, isAudioEnabled=$isAudioEnabled, " +
                "isVideoEnabled=$isVideoEnabled, isSelected=$isSelected, isSelf=$isSelf, " +
                "hasRaisedHand=$hasRaisedHand, handRaiseTimestamp=$handRaiseTimestamp, isModerator=$isModerator, " +
                "connectionState=$connectionState, isSpeaking=$isSpeaking)"
    }
}
