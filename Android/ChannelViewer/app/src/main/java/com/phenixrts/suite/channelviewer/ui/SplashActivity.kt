/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.*
import com.phenixrts.suite.channelviewer.common.enums.ExpressError
import com.phenixrts.suite.channelviewer.databinding.ActivitySplashBinding
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.common.launchUI
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixcore.repositories.models.PhenixEvent
import com.phenixrts.suite.phenixdeeplinks.models.DeepLinkStatus
import com.phenixrts.suite.phenixdeeplinks.models.PhenixDeepLinkConfiguration
import com.phenixrts.suite.phenixdeeplinks.DeepLinkActivity
import com.phenixrts.suite.phenixdeeplinks.common.init
import timber.log.Timber
import javax.inject.Inject

private const val TIMEOUT_DELAY = 10000L

@SuppressLint("CustomSplashScreen")
class SplashActivity : DeepLinkActivity() {

    @Inject lateinit var phenixCore: PhenixCore

    private lateinit var binding: ActivitySplashBinding
    private var channelToJoin = ""

    private val viewModel by lazyViewModel({ application as ChannelViewerApplication }) {
        ChannelViewModel(phenixCore)
    }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        launchMain {
            binding.root.showSnackBar(getString(R.string.err_network_problems))
        }
    }

    override fun isAlreadyInitialized() = phenixCore.isInitialized

    override val additionalConfiguration: HashMap<String, String>
        get() = hashMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        ChannelViewerApplication.component.inject(this)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        launchUI {
            phenixCore.onError.collect { error ->
                if (error == PhenixError.FAILED_TO_INITIALIZE) {
                    Timber.d("Splash: Failed to initialize Phenix Core: $error")
                    showErrorDialog(error.message)
                }
            }
        }
        launchUI {
            phenixCore.onEvent.collect { event ->
                Timber.d("Splash: Phenix core event: $event")
                if (event == PhenixEvent.PHENIX_CORE_INITIALIZED) {
                    Timber.d("Joining channel: $channelToJoin")
                    viewModel.joinChannel(channelToJoin)
                    showLandingScreen()
                }
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onDeepLinkQueried(
        status: DeepLinkStatus,
        configuration: PhenixDeepLinkConfiguration,
        rawConfiguration: Map<String, String>,
        deepLink: String
    ) {
        launchUI {
            when (status) {
                DeepLinkStatus.RELOAD -> showErrorDialog(getErrorMessage(ExpressError.CONFIGURATION_CHANGED_ERROR))
                DeepLinkStatus.READY -> initializePhenixCore(configuration)
            }
        }
    }

    private fun initializePhenixCore(configuration: PhenixDeepLinkConfiguration) {
        Timber.d("Channel viewer started with configuration: $configuration")
        if (configuration.selectedAlias.isEmpty() && configuration.channels.isEmpty()) {
            showErrorDialog(getErrorMessage(ExpressError.DEEP_LINK_ERROR))
            return
        }
        channelToJoin = configuration.selectedAlias.takeIf { it.isNotEmpty() } ?: configuration.channels.first()
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_DELAY)
        phenixCore.init(configuration)
    }

    private fun showLandingScreen() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        Timber.d("Navigating to Landing Screen")
        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
        finish()
    }
}
