/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

import java.util.*

data class PhenixMessageConfiguration(
    val mimeType: String = "",
    val batchSize: Int = 0,
    val joinedDate: Long = Date().time
)
