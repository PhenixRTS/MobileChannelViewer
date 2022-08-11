/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixMessage(
    val alias: String,
    val messageId: String,
    val messageDate: Long,
    val messageMimeType: String,
    val message: String,
    val memberId: String,
    val memberRole: PhenixMemberRole,
    val memberName: String
)
