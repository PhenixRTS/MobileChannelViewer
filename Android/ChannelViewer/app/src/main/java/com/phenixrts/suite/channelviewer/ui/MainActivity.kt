/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.google.android.material.snackbar.Snackbar
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.showInvalidDeepLinkDialog
import com.phenixrts.suite.channelviewer.common.lazyViewModel
import com.phenixrts.suite.channelviewer.common.showSnackBar
import com.phenixrts.suite.channelviewer.databinding.ActivityMainBinding
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelState
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixdebugmenu.models.DebugEvent
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
                Timber.d("Main: Phenix Core error: $error")
                when (error) {
                    PhenixError.FAILED_TO_INITIALIZE -> showInvalidDeepLinkDialog()
                    else -> { /* Ignored */ }
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
        viewModel.observeDebugMenu(
            binding.debugMenu,
            onError = { error ->
                binding.root.showSnackBar(error, Snackbar.LENGTH_LONG)
            },
            onEvent = { event ->
                when (event) {
                    DebugEvent.SHOW_MENU -> binding.debugMenu.showAppChooser(this@MainActivity)
                    DebugEvent.FILES_DELETED -> binding.root.showSnackBar(getString(R.string.files_deleted), Snackbar.LENGTH_LONG)
                }
            }
        )
        binding.debugMenu.onStart(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.debugMenu.isOpened()) {
                    binding.debugMenu.hide()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.debugMenu.onStop()
    }
}
