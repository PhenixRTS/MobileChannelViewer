/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.common

import com.phenixrts.express.PCastExpress
import com.phenixrts.pcast.PCast
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun PCastExpress.waitForOnline() = suspendCoroutine<Unit> { continuation ->
    waitForOnline {
        continuation.resume(Unit)
    }
}

fun PCast.collectLogs(onCollected: (String) -> Unit) {
    collectLogMessages { _, _, messages ->
        onCollected(messages)
    }
}