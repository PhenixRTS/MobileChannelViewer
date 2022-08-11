/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdeeplinks.cache

import android.content.Context
import android.content.SharedPreferences

private const val APP_PREFERENCES = "deep_link_preferences"
private const val CONFIGURATION = "configuration"

internal class ConfigurationProvider(private val context: Context) {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
    }

    fun saveConfiguration(configuration: String?) {
        preferences.edit().putString(CONFIGURATION, configuration).apply()
    }

    fun getConfiguration(): String = preferences.getString(CONFIGURATION, "") ?: ""

    fun hasConfiguration(): Boolean = getConfiguration().isNotBlank()
}
