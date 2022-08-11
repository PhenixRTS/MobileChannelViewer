/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaptions.models

import com.phenixrts.suite.phenixclosedcaptions.common.px

data class ClosedCaptionConfiguration(
    var anchorPointOnTextWindow: AnchorPosition = AnchorPosition(0.5f, 1f),
    var positionOfTextWindow: AnchorPosition = AnchorPosition(0.5f, 0.95f),
    var widthInCharacters: Int = 32,
    var heightInTextLines: Int = 15,
    var backgroundColor: String = "#000000",
    var backgroundAlpha: Float = 1f,
    var backgroundFlashing: Boolean = false,
    var visible: Boolean = true,
    var zOrder: Int = 0,
    var printDirection: String = "left-to-right",
    var scrollDirection: String = "top-to-bottom",
    var justify: String = JustificationMode.CENTER.value,
    var wordWrap: Boolean = true,
    var effectType: String = "pop-on",
    var effectDurationInSeconds: Int = 1,
    var paddingStart: Int = 4.px,
    var paddingEnd: Int = 4.px,
    val paddingTop: Int = 1.px,
    val paddingBottom: Int = 1.px,
    var textColor: String = "#f4f4f4",
    var isEnabled: Boolean = true,
    var isButtonVisible: Boolean = true
)
