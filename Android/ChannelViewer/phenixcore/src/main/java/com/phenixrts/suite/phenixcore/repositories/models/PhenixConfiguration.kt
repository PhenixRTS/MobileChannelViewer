/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

data class PhenixConfiguration(
    val authToken: String? = null,
    val streamToken: String? = null,
    val publishToken: String? = null,
    val streamIDs: List<String> = listOf(),
    val channelAliases: List<String> = listOf(),
    val channelStreamTokens: List<String> = listOf(),
    val roomAliases: List<String> = listOf(),
    val roomAudioStreamToken: String? = null,
    val roomVideoStreamToken: String? = null,
    val uri: String? = null,
    val url: String? = null,
    val acts: List<String> = listOf(),
    val mimeTypes: List<String> = listOf(),
    val maxVideoSubscriptions: Int? = null,
    val selectedAlias: String? = null,
    var publishingEnabled: Boolean = false,
    var enableDebugging: Boolean = false,
    var logLevel: PhenixLogLevel = PhenixLogLevel.INFO
)

enum class PhenixLogLevel(val level: String) {
    VERBOSE("verbose"),
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error")
}
