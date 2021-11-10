/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.closedcaptions.models

internal enum class JustificationMode(val value: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    FULL("full");

    internal companion object {
        private val valuesMap = values().associateBy { it.value }
        fun fromString(value: String) = valuesMap[value] ?: LEFT
    }
}
