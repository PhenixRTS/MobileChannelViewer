/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

import com.phenixrts.room.MemberRole

data class PhenixRoomConfiguration(
    val roomAlias: String,
    val memberName: String,
    val memberRole: MemberRole,
    val messageConfigs: List<PhenixMessageConfiguration> = emptyList(),
    var joinSilently: Boolean = false
)
