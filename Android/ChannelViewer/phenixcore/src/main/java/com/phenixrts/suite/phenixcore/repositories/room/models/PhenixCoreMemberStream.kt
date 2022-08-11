/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.room.models

import com.phenixrts.room.Stream

data class PhenixCoreMemberStream(
    val stream: Stream,
    var isSubscribed: Boolean = false
)
