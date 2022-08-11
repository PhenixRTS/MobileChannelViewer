/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixMediaState(
    val isVideoEnabled: Boolean,
    val isAudioEnabled: Boolean,
    val cameraFacingMode: PhenixFacingMode
)
