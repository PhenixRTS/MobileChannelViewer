/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.common

import com.phenixrts.suite.phenixcore.deeplink.models.PhenixDeepLinkConfiguration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

inline fun <reified T> T.toJson() = json.encodeToString(this)

inline fun <reified T> String.asObject(): T = json.decodeFromString(this)

internal fun HashMap<String, String>.asConfigurationModel() =
    json.decodeFromString<PhenixDeepLinkConfiguration>(JSONObject(this as Map<*, *>).toString())
