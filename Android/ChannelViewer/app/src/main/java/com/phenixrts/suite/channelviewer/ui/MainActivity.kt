/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.showInvalidDeepLinkDialog
import com.phenixrts.suite.channelviewer.common.enums.StreamStatus
import com.phenixrts.suite.channelviewer.common.launchMain
import com.phenixrts.suite.channelviewer.common.lazyViewModel
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcommon.DebugMenu
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcommon.common.showToast
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

const val EXTRA_DEEP_LINK_MODEL = "ExtraDeepLinkModel"

class MainActivity : AppCompatActivity() {

    @Inject lateinit var channelExpressRepository: ChannelExpressRepository
    @Inject lateinit var fileWriterTree: FileWriterDebugTree

    private val viewModel by lazyViewModel { ChannelViewModel(channelExpressRepository) }
    private val debugMenu: DebugMenu by lazy {
        DebugMenu(fileWriterTree, channelExpressRepository.roomExpress, main_root, { files ->
            debugMenu.showAppChooser(this, files)
        }, { error ->
            showToast(getString(error))
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelViewerApplication.component.inject(this)
        setContentView(R.layout.activity_main)

        menu_overlay.setOnClickListener {
            debugMenu.onScreenTapped()
        }
        viewModel.onChannelExpressError.observe(this, Observer {
            Timber.d("Channel Express failed")
            showInvalidDeepLinkDialog()
        })
        viewModel.onChannelState.observe(this, Observer { status ->
            Timber.d("Stream state changed: $status")
            if (status == StreamStatus.ONLINE) {
                offline_view.visibility = View.GONE
            } else {
                offline_view.visibility = View.VISIBLE
            }
            if (status == StreamStatus.FAILED) {
                Timber.d("Stream failed")
                showInvalidDeepLinkDialog()
            }
        })

        checkDeepLink(intent)
        viewModel.updateSurfaceHolder(channel_surface.holder)
        debugMenu.onStart(getString(R.string.debug_app_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        ), getString(R.string.debug_sdk_version,
            com.phenixrts.sdk.BuildConfig.VERSION_NAME,
            com.phenixrts.sdk.BuildConfig.VERSION_CODE
        ))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("On new intent $intent")
        checkDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        debugMenu.onStop()
    }

    override fun onBackPressed() {
        if (debugMenu.isClosed()){
            super.onBackPressed()
        }
    }

    private fun checkDeepLink(intent: Intent?) = launchMain {
        intent?.let { intent ->
            if (intent.hasExtra(EXTRA_DEEP_LINK_MODEL)) {
                (intent.getStringExtra(EXTRA_DEEP_LINK_MODEL))?.let { channelAlias ->
                    Timber.d("Received channel code: $channelAlias")
                    viewModel.joinChannel(channelAlias, channel_surface.holder)
                }
                intent.removeExtra(EXTRA_DEEP_LINK_MODEL)
                return@launchMain
            }
        }
    }

}
