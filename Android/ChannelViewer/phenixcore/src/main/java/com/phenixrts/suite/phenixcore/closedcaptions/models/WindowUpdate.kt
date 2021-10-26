/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.closedcaptions.models

import kotlinx.serialization.Serializable

@Serializable
internal data class WindowUpdate(
    val windowIndex: Int? = null,
    val anchorPointOnTextWindow: AnchorPosition? = null,
    val positionOfTextWindow: AnchorPosition? = null,
    val widthInCharacters: Int? = null,
    val heightInTextLines: Int? = null,
    val backgroundColor: String? = null,
    val backgroundAlpha: Float? = null,
    val backgroundFlashing: Boolean? = null,
    val visible: Boolean? = null,
    val zOrder: Int? = null,
    val printDirection: String? = null,
    val scrollDirection: String? = null,
    val justify: String? = null,
    val wordWrap: Boolean? = null,
    val effectType: String? = null,
    val effectDurationInSeconds: Int? = null
)
