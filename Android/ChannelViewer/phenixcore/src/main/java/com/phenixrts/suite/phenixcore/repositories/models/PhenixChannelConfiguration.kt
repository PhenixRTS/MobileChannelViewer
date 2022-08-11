/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixChannelConfiguration(
    val channelAlias: String? = null,
    val channelID: String? = null,
    val streamToken: String? = null,
    val publishToken: String? = null,
    val channelCapabilities: List<String> = emptyList()
)
