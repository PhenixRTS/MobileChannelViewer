/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.enums.ConnectionStatus
import com.phenixrts.suite.channelviewer.common.showInvalidDeepLinkDialog
import com.phenixrts.suite.channelviewer.common.lazyViewModel
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcommon.DebugMenu
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcommon.common.showToast
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject lateinit var channelExpress: ChannelExpressRepository
    @Inject lateinit var fileWriterTree: FileWriterDebugTree

    private val viewModel: ChannelViewModel by lazyViewModel({ application as ChannelViewerApplication }) {
        ChannelViewModel(channelExpress)
    }
    private val debugMenu: DebugMenu by lazy {
        DebugMenu(fileWriterTree, channelExpress.roomExpress, main_root, { files ->
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
        viewModel.onChannelExpressError.observe(this, {
            Timber.d("Channel Express failed")
            showInvalidDeepLinkDialog()
        })
        viewModel.onChannelState.observe(this, { status ->
            Timber.d("Stream state changed: $status")
            if (status == ConnectionStatus.ONLINE) {
                offline_view.visibility = View.GONE
            } else {
                offline_view.visibility = View.VISIBLE
            }
            if (status == ConnectionStatus.FAILED) {
                Timber.d("Stream failed")
                showInvalidDeepLinkDialog()
            }
        })

        viewModel.mimeTypes.observe(this, { mimeTypes ->
            channelExpress.roomService?.let { service ->
                closed_caption_view.subscribe(service, mimeTypes)
            }
        })

        viewModel.updateSurfaceHolder(channel_surface.holder)
        debugMenu.onStart(getString(R.string.debug_app_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        ), getString(R.string.debug_sdk_version,
            com.phenixrts.sdk.BuildConfig.VERSION_NAME,
            com.phenixrts.sdk.BuildConfig.VERSION_CODE
        ))
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
}
