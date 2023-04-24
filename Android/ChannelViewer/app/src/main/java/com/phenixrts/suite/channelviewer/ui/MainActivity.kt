/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.phenixrts.common.AuthenticationStatus
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.channelviewer.common.lazyViewModel
import com.phenixrts.suite.channelviewer.common.showInvalidDeepLinkDialog
import com.phenixrts.suite.channelviewer.common.showSnackBar
import com.phenixrts.suite.channelviewer.databinding.ActivityMainBinding
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcommon.common.launchMain
import com.phenixrts.suite.phenixdebugmenu.models.DebugEvent
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject lateinit var channelExpress: ChannelExpressRepository
    @Inject lateinit var fileWriterTree: FileWriterDebugTree
    private lateinit var binding: ActivityMainBinding

    private val viewModel: ChannelViewModel by lazyViewModel({ application as ChannelViewerApplication }) {
        ChannelViewModel(channelExpress)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ChannelViewerApplication.component.inject(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        launchMain {
            viewModel.onChannelExpressError.collect{
                Timber.d("Channel Express failed")
                showInvalidDeepLinkDialog()
            }
        }

        launchMain {
            viewModel.onChannelState.collect { status ->
                Timber.d("Stream state changed: $status")

                if (status == ConnectionStatus.ONLINE) {
                    binding.offlineView.root.visibility = View.GONE
                } else {
                    binding.offlineView.root.visibility = View.VISIBLE
                }

                if (status == ConnectionStatus.FAILED) {
                    Timber.d("Stream failed")
                    showInvalidDeepLinkDialog()
                }
            }
        }

        launchMain {
            viewModel.mimeTypes.collect { mimeTypes ->
                channelExpress.roomService?.let { service ->
                    binding.closedCaptionView.subscribe(service, mimeTypes)
                }
            }
        }

        viewModel.updateSurfaceHolder(binding.channelSurface.holder)

        binding.debugMenu.onStart(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
        binding.debugMenu.observeDebugMenu(
            fileWriterTree,
            "${BuildConfig.APPLICATION_ID}.provider",
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.debugMenu.isOpened()) {
                    binding.debugMenu.hide()
                } else {
                    finish()
                }
            }
        })

        launchMain {
            // Handle authentication token expiration in case of re-connection (e.g. network loss).
            viewModel.onAuthenticationStatus.collect { status ->
                Timber.d("Authentication status changed to [$status]")
                if (status == AuthenticationStatus.UNAUTHENTICATED) {
                    // Fetch a new authentication token and use it.
                    // val authenticationToken = ...
                    // viewModel.updateAuthenticationToken(authenticationToken)
                }
            }
        }

        launchMain {
            viewModel.joinChannel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.debugMenu.onStop()
    }
}
