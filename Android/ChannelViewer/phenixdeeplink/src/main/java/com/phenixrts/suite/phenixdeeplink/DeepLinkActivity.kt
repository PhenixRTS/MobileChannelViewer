/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdeeplink

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phenixrts.suite.phenixdeeplink.cache.ConfigurationProvider
import com.phenixrts.suite.phenixdeeplink.common.QUERY_AUTH_TOKEN
import com.phenixrts.suite.phenixdeeplink.common.QUERY_EDGE_TOKEN
import com.phenixrts.suite.phenixdeeplink.common.QUERY_MIME_TYPES
import com.phenixrts.suite.phenixdeeplink.common.QUERY_PUBLISH_TOKEN
import org.json.JSONObject
import timber.log.Timber

abstract class DeepLinkActivity : AppCompatActivity() {

    private val configurationProvider by lazy { ConfigurationProvider(this) }

    abstract val additionalConfiguration: HashMap<String, String>

    val configuration: HashMap<String, String> = hashMapOf(
        QUERY_EDGE_TOKEN to "",
        QUERY_AUTH_TOKEN to "",
        QUERY_PUBLISH_TOKEN to "",
        QUERY_MIME_TYPES to BuildConfig.MIME_TYPES
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.run {
            Timber.d("Deep Link activity created")
            updateConfiguration(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.run {
            Timber.d("Deep Link activity received new intent")
            updateConfiguration(this)
        }
    }

    private fun updateConfiguration(intent: Intent) {
        configuration.putAll(additionalConfiguration)
        Timber.d("Checking deep link: ${intent.data}, $configuration")

        if (configurationProvider.hasConfiguration()) {
            JSONObject(configurationProvider.getConfiguration()).run {
                Timber.d("Loading saved configuration: $this")
                keys().forEach { key ->
                    configuration[key] = getString(key)
                }
            }
        } else {
            intent.data?.let { deepLink ->
                Timber.d("Loading configuration from deep link: $deepLink")

                configuration.keys.forEach { key ->
                    deepLink.getQueryParameter(key)?.let { value ->
                        configuration[key] = value
                    }
                }

                if (isAlreadyInitialized()) {
                    Timber.d("Configuration already loaded")
                    configurationProvider.saveConfiguration(JSONObject(configuration as Map<*, *>).toString())
                    onDeepLinkQueried(DeepLinkStatus.RELOAD)
                    return
                }
            }
        }
        Timber.d("Configuration updated: $configuration")
        configurationProvider.saveConfiguration(null)
        onDeepLinkQueried(DeepLinkStatus.READY)
    }

    abstract fun isAlreadyInitialized(): Boolean

    abstract fun onDeepLinkQueried(status: DeepLinkStatus)
}

enum class DeepLinkStatus {
    RELOAD,
    READY
}
