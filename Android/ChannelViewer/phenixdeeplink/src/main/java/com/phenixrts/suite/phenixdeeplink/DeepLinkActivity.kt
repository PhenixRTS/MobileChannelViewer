/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdeeplink

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phenixrts.suite.phenixdeeplink.cache.ConfigurationProvider
import org.json.JSONObject
import timber.log.Timber

private const val QUERY_STAGING = "https://stg.phenixrts.com"
private const val QUERY_CHANNEL = "#"
const val QUERY_URI = "uri"
const val QUERY_BACKEND = "backend"
const val QUERY_EDGE_AUTH = "edgeauth"
const val QUERY_MIME_TYPES = "mimetypes"
const val QUERY_CHANNEL_ALIAS = "channelalias"

abstract class DeepLinkActivity : AppCompatActivity() {

    private val configurationProvider by lazy { ConfigurationProvider(this) }

    abstract val additionalConfiguration: HashMap<String, String>

    val configuration: HashMap<String, String> = hashMapOf(
        QUERY_URI to BuildConfig.PCAST_URL,
        QUERY_BACKEND to BuildConfig.BACKEND_URL,
        QUERY_EDGE_AUTH to "",
        QUERY_MIME_TYPES to BuildConfig.MIME_TYPES,
        QUERY_CHANNEL_ALIAS to ""
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
                val isStagingUri = deepLink.toString().startsWith(QUERY_STAGING)
                Timber.d("Loading configuration from deep link: $deepLink")
                deepLink.toString().takeIf { it.contains(QUERY_CHANNEL) }?.substringAfterLast(QUERY_CHANNEL)?.run {
                    configuration[QUERY_CHANNEL_ALIAS] = this
                }
                configuration.keys.forEach { key ->
                    when (key) {
                        QUERY_URI -> {
                            val value = deepLink.getQueryParameter(QUERY_URI) ?: if (isStagingUri)
                                BuildConfig.STAGING_PCAST_URL else BuildConfig.PCAST_URL
                            configuration[key] = value
                        }
                        QUERY_BACKEND -> {
                            val value = deepLink.getQueryParameter(QUERY_BACKEND) ?: if (isStagingUri)
                                BuildConfig.STAGING_BACKEND_URL else BuildConfig.BACKEND_URL
                            configuration[key] = value
                        }
                        else -> {
                            deepLink.getQueryParameter(key)?.let { value ->
                                configuration[key] = value
                            }
                        }
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
