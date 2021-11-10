/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixMessageConfiguration(
    val mimeTypes: List<String>,
    val batchSize: Int
)
