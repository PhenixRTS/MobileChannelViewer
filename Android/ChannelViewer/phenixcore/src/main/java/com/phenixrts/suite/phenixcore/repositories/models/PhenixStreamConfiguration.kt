/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixStreamConfiguration(
    val streamID: String? = null,
    val streamToken: String? = null,
    val capabilities: List<String> = emptyList()
)
