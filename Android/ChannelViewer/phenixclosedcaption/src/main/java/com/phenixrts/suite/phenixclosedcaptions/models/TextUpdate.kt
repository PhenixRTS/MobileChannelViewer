/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaptions.models

import kotlinx.serialization.Serializable

@Serializable
data class TextUpdate(
    val timestamp: Long = 0,
    val caption: String = "",
    val windowIndex: Int = 0
)
