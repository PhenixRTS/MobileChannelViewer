/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.room.models

@Suppress("unused")
internal enum class PhenixCoreAudioLevel(private val range: IntRange) {
    VOLUME_0(Short.MIN_VALUE .. -70),
    VOLUME_1(-70 .. -60),
    VOLUME_2(-60 .. -50),
    VOLUME_3(-50 .. -45),
    VOLUME_4(-45 .. -40),
    VOLUME_5(-40 .. -35),
    VOLUME_6(-35 .. -30),
    VOLUME_7(-30 .. -25),
    VOLUME_8(-25 .. -20),
    VOLUME_9(-20 .. Short.MAX_VALUE);

    companion object {
        fun getVolume(decibels: Double) = values().find { decibels.toInt() in it.range } ?: VOLUME_0
    }
}
