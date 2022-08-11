/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixPublishConfiguration(
    val cameraFacingMode: PhenixFacingMode = PhenixFacingMode.USER,
    val cameraFps: Double = 15.0,
    val isAudioEnabled: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val echoCancellationMode: PhenixAudioEchoCancelationMode = PhenixAudioEchoCancelationMode.OFF,
)
