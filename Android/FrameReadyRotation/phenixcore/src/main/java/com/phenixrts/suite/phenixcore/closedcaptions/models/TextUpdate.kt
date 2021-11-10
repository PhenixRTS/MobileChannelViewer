/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.closedcaptions.models

import kotlinx.serialization.Serializable

@Serializable
internal data class TextUpdate(
    val timestamp: Long = 0,
    val caption: String = "",
    val windowIndex: Int = 0
)
