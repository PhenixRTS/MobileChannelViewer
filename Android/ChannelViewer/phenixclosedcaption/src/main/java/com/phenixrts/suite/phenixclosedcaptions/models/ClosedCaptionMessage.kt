/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaptions.models

import kotlinx.serialization.Serializable

@Serializable
data class ClosedCaptionMessage(
    val windowUpdates: List<WindowUpdate> = listOf(),
    val textUpdates: List<TextUpdate> = listOf(),
    val windowIndex: Int = 0,
    val serviceId: String = ""
)
