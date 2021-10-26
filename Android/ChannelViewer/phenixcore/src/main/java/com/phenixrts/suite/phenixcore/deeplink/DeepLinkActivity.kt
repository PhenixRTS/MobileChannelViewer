/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.deeplink

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phenixrts.suite.phenixcore.BuildConfig
import com.phenixrts.suite.phenixcore.common.asConfigurationModel
import com.phenixrts.suite.phenixcore.common.toJson
import com.phenixrts.suite.phenixcore.deeplink.cache.ConfigurationProvider
import com.phenixrts.suite.phenixcore.deeplink.models.*
import org.json.JSONObject
import timber.log.Timber

abstract class DeepLinkActivity : AppCompatActivity() {

    private val configurationProvider by lazy { ConfigurationProvider(this) }
    private val configuration: HashMap<String, String> = hashMapOf(
        QUERY_AUTH_TOKEN to "",
        QUERY_ACTS to "",
        QUERY_BACKEND to BuildConfig.BACKEND_URL,
        QUERY_CHANNEL_ALIASES to "",
        QUERY_EDGE_TOKEN to "",
        QUERY_STREAM_TOKENS to "",
        QUERY_MIME_TYPES to BuildConfig.MIME_TYPES,
        QUERY_PUBLISH_TOKEN to "",
        QUERY_STREAM_IDS to "",
        QUERY_URI to BuildConfig.PCAST_URL,
        QUERY_VIDEO_COUNT to BuildConfig.MAX_VIDEO_MEMBERS
    )

    abstract val additionalConfiguration: HashMap<String, String>

    abstract fun isAlreadyInitialized(): Boolean

    abstract fun onDeepLinkQueried(
        status: DeepLinkStatus,
        configuration: PhenixDeepLinkConfiguration,
        rawConfiguration: Map<String, String>,
        deepLink: String
    )

    open var defaultBackend = BuildConfig.BACKEND_URL
    open var defaultStagingBackend = BuildConfig.STAGING_BACKEND_URL
    open var defaultUri = BuildConfig.PCAST_URL
    open var defaultStagingUri = BuildConfig.STAGING_PCAST_URL

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
        var path = ""
        var status = DeepLinkStatus.READY
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
                path = deepLink.toString()
                val isStagingUri = path.contains(STAGING_URI)
                Timber.d("Loading configuration from deep link: $deepLink")
                path.takeIf { it.contains(QUERY_CHANNEL) }?.substringAfterLast(QUERY_CHANNEL)?.run {
                    configuration[QUERY_CHANNEL_ALIASES] = this
                }
                configuration.keys.forEach { key ->
                    when (key) {
                        QUERY_URI -> {
                            val value = deepLink.getQueryParameter(QUERY_URI) ?: if (isStagingUri)
                                defaultStagingUri else defaultUri
                            configuration[key] = value
                        }
                        QUERY_BACKEND -> {
                            val value = deepLink.getQueryParameter(QUERY_BACKEND) ?: if (isStagingUri)
                                defaultStagingBackend else defaultBackend
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
                    configurationProvider.saveConfiguration(configuration.toJson())
                    status = DeepLinkStatus.RELOAD
                    onDeepLinkQueried(status, configuration.asConfigurationModel(), configuration, path)
                    return
                }
            }
        }
        Timber.d("Configuration updated: $configuration")
        configurationProvider.saveConfiguration(null)
        onDeepLinkQueried(status, configuration.asConfigurationModel(), configuration, path)
    }


}
