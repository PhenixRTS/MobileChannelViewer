/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models
import com.phenixrts.room.MemberRole

data class PhenixMessage(
    val messageId: String,
    val messageDate: Long,
    val messageMimeType: String,
    val message: String,
    val memberId: String,
    val memberRole: MemberRole,
    val memberName: String
)
