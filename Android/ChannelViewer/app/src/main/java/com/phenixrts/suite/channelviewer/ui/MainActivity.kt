/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.showInvalidDeepLinkDialog
import com.phenixrts.suite.channelviewer.common.lazyViewModel
import com.phenixrts.suite.channelviewer.databinding.ActivityMainBinding
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcore.debugmenu.common.showToast
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelState
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixcore.repositories.models.PhenixEvent
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject lateinit var phenixCore: PhenixCore
    private lateinit var binding: ActivityMainBinding

    private val viewModel: ChannelViewModel by lazyViewModel({ application as ChannelViewerApplication }) {
        ChannelViewModel(phenixCore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelViewerApplication.component.inject(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        launchMain {
            phenixCore.onError.collect { error ->
                phenixCore.consumeLastError()
                Timber.d("Main: Phenix Core error: $error")
                when (error) {
                    PhenixError.FAILED_TO_COLLECT_LOGS -> showToast(error.message)
                    PhenixError.FAILED_TO_INITIALIZE -> showInvalidDeepLinkDialog()
                    else -> { /* Ignored */ }
                }
            }
        }
        launchMain {
            phenixCore.onEvent.collect { event ->
                phenixCore.consumeLastEvent()
                Timber.d("Main: Phenix core event: $event")
                if (event == PhenixEvent.SHOW_DEBUG_MENU_APP_CHOOSER) {
                    binding.debugMenu.showAppChooser(this@MainActivity)
                }
            }
        }
        launchMain {
            viewModel.onChannelState.collect { status ->
                Timber.d("Stream state changed: $status")
                if (status == PhenixChannelState.STREAMING) {
                    viewModel.updateSurface(binding.channelSurface)
                    viewModel.subscribeToClosedCaptions(binding.closedCaptionView)
                    binding.offlineView.root.visibility = View.GONE
                } else {
                    binding.offlineView.root.visibility = View.VISIBLE
                }
            }
        }

        viewModel.updateSurface(binding.channelSurface)
        viewModel.observeDebugMenu(binding.debugMenu)
        binding.debugMenu.onStart(getString(R.string.debug_app_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        ), getString(R.string.debug_sdk_version,
            com.phenixrts.sdk.BuildConfig.VERSION_NAME,
            com.phenixrts.sdk.BuildConfig.VERSION_CODE
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.debugMenu.onStop()
    }

    override fun onBackPressed() {
        if (binding.debugMenu.isClosed()){
            super.onBackPressed()
        }
    }
}
