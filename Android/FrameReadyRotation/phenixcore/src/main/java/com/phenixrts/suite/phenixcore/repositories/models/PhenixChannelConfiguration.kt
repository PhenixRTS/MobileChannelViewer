/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixChannelConfiguration(
    val channelAlias: String,
    val streamToken: String? = null,
    val channelCapabilities: List<String> = emptyList()
)
