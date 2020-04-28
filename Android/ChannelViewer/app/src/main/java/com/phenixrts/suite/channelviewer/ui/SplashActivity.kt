/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.*
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import kotlinx.android.synthetic.main.activity_splash.*
import timber.log.Timber
import javax.inject.Inject

private const val TIMEOUT_DELAY = 5000L

class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var channelExpressRepository: ChannelExpressRepository

    private val timeoutHandler = Handler()
    private val timeoutRunnable = Runnable {
        launchMain {
            showSnackBar(getString(R.string.err_network_problems))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelViewerApplication.component.inject(this)
        setContentView(R.layout.activity_splash)
        channelExpressRepository.onChannelExpressError.observe(this, Observer {
            Timber.d("Channel express failed")
            showInvalidDeepLinkDialog()
        })
        checkDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("On new intent $intent")
        checkDeepLink(intent)
    }

    private fun checkDeepLink(intent: Intent?) {
        Timber.d("Checking deep link: ${intent?.data}")
        var channelAlias: String? = null
        if (intent?.data != null) {
            intent.data?.let { data ->
                channelAlias = data.toString().takeIf { it.contains(QUERY_CHANNEL) }?.substringAfterLast(QUERY_CHANNEL)
                val isStagingUri = data.toString().startsWith(QUERY_STAGING)
                val uri = data.getQueryParameter(QUERY_URI) ?: if (isStagingUri) BuildConfig.STAGING_PCAST_URL else BuildConfig.PCAST_URL
                val backend = data.getQueryParameter(QUERY_BACKEND) ?: if (isStagingUri) BuildConfig.STAGING_BACKEND_URL else BuildConfig.BACKEND_URL
                val configuration = ChannelConfiguration(uri, backend)
                Timber.d("Checking deep link: $channelAlias $uri $backend")
                if (channelExpressRepository.hasConfigurationChanged(configuration)) {
                    reloadConfiguration(configuration)
                }
            }
        }
        showLandingScreen(channelAlias)
    }

    private fun reloadConfiguration(configuration: ChannelConfiguration) {
        launchMain{
            channelExpressRepository.setupChannelExpress(configuration)
        }
    }

    private fun showSnackBar(message: String) = launchMain {
        Snackbar.make(splash_root, message, Snackbar.LENGTH_INDEFINITE).show()
    }

    private fun showLandingScreen(channelAlias: String?) = launchMain {
        if (channelAlias == null) {
            showInvalidDeepLinkDialog()
            return@launchMain
        }
        Timber.d("Waiting for PCast")
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_DELAY)
        channelExpressRepository.waitForPCast()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        Timber.d("Navigating to Landing Screen")
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        intent.putExtra(EXTRA_DEEP_LINK_MODEL, channelAlias)
        startActivity(intent)
        finish()
    }
}
