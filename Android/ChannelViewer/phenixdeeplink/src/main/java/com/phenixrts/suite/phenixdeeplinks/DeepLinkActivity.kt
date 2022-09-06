/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdeeplinks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phenixrts.suite.phenixcore.common.json
import com.phenixrts.suite.phenixcore.common.toJson
import com.phenixrts.suite.phenixdeeplinks.cache.ConfigurationProvider
import com.phenixrts.suite.phenixdeeplinks.models.*
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import timber.log.Timber

abstract class DeepLinkActivity : AppCompatActivity() {

    private val configurationProvider by lazy { ConfigurationProvider(this) }
    private val configuration: HashMap<String, String> = hashMapOf(
        QUERY_AUTH_TOKEN to "",
        QUERY_EDGE_TOKEN to "",
        QUERY_PUBLISH_TOKEN to "",
        QUERY_TOKEN to "",
        QUERY_URI to BuildConfig.PCAST_URL,
        QUERY_ACTS to "",
        QUERY_MIME_TYPES to BuildConfig.MIME_TYPES,
        QUERY_URL to "",
        QUERY_VIDEO_COUNT to BuildConfig.MAX_VIDEO_RENDERERS.toString(),
        QUERY_STREAM_IDS to "",
        QUERY_CHANNEL_ALIASES to "",
        QUERY_CHANNEL_TOKENS to "",
        QUERY_ROOM_ALIASES to "",
        QUERY_ROOM_AUDIO_TOKEN to "",
        QUERY_ROOM_VIDEO_TOKEN to "",
        QUERY_SELECTED_ALIAS to "",
        QUERY_PUBLISHING_ENABLED to ""
    )

    abstract val additionalConfiguration: HashMap<String, String>

    abstract fun isAlreadyInitialized(): Boolean

    abstract fun onDeepLinkQueried(
        status: DeepLinkStatus,
        configuration: PhenixDeepLinkConfiguration,
        rawConfiguration: Map<String, String>,
        deepLink: String
    )

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
                try {
                    Uri.parse(path).fragment?.run {
                        configuration[QUERY_SELECTED_ALIAS] = this
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get uri fragment")
                }
                configuration.keys.forEach { key ->
                    when (key) {
                        QUERY_URI -> {
                            val value = deepLink.getQueryParameter(QUERY_URI) ?: if (isStagingUri)
                                defaultStagingUri else defaultUri
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

private fun HashMap<String, String>.asConfigurationModel() =
    json.decodeFromString<PhenixDeepLinkConfiguration>(JSONObject(this as Map<*, *>).toString())
