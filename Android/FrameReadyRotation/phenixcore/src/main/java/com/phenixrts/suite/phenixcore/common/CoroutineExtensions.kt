/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.common

import kotlinx.coroutines.*
import timber.log.Timber

private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun launchMain(block: suspend CoroutineScope.() -> Unit) = mainScope.launch(
    context = CoroutineExceptionHandler { _, e ->
        Timber.w(e, "Coroutine failed: ${e.localizedMessage}")
        e.printStackTrace()
    },
    block = block
)

fun launchIO(block: suspend CoroutineScope.() -> Unit) = ioScope.launch(
    context = CoroutineExceptionHandler { _, e ->
        Timber.w(e, "Coroutine failed: ${e.localizedMessage}")
        e.printStackTrace()
    },
    block = block
)
