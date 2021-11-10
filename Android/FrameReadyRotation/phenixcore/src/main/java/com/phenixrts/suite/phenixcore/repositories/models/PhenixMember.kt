/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

import com.phenixrts.room.MemberRole

data class PhenixMember(
    val id: String,
    val name: String,
    val role: MemberRole,
    val isAudioEnabled: Boolean,
    val isVideoEnabled: Boolean,
    val isSelected: Boolean,
    val isSpeaking: Boolean,
    val isSelf: Boolean,
    val hasRaisedHand: Boolean
) {
    val isModerator = role == MemberRole.MODERATOR

    override fun toString(): String {
        return "PhenixMember(id='$id', name='$name', role=$role, isAudioEnabled=$isAudioEnabled, " +
                "isVideoEnabled=$isVideoEnabled, isSelected=$isSelected, isSpeaking=$isSpeaking, " +
                "isSelf=$isSelf, hasRaisedHand=$hasRaisedHand, isModerator=$isModerator)"
    }
}
