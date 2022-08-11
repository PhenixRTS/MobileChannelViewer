/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.chat.models

import com.phenixrts.chat.RoomChatService
import com.phenixrts.common.Disposable

data class PhenixChatService(
    val alias: String,
    val mimeType: String,
    val service: RoomChatService,
    var disposable: Disposable? = null
)
