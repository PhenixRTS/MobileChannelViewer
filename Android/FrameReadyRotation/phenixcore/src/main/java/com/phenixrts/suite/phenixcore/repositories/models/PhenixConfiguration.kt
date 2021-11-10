/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixConfiguration(
    val backend: String? = null,
    val uri: String? = null,
    val authToken: String? = null,
    val streamTokens: List<String> = listOf(),
    val channels: List<String> = listOf(),
    val streams: List<String> = listOf(),
    val mimeTypes: List<String> = listOf()
)
