/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
import com.phenixrts.suite.channelviewer.common.closeApp
import com.phenixrts.suite.channelviewer.common.enums.StreamStatus
import com.phenixrts.suite.channelviewer.common.launchMain
import com.phenixrts.suite.channelviewer.common.lazyViewModel
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.channelviewer.ui.viewmodel.ChannelViewModel
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

const val EXTRA_DEEP_LINK_MODEL = "ExtraDeepLinkModel"

class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var channelExpressRepository: ChannelExpressRepository
    private val viewModel by lazyViewModel { ChannelViewModel(channelExpressRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelViewerApplication.component.inject(this)
        setContentView(R.layout.activity_main)

        viewModel.onChannelExpressError.observe(this, Observer {
            Timber.d("Channel Express failed")
            closeApp()
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
                closeApp()
            }
        })

        checkDeepLink(intent)
        viewModel.updateSurfaceHolder(channel_surface.holder)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("On new intent $intent")
        checkDeepLink(intent)
    }

    private fun checkDeepLink(intent: Intent?) = launchMain {
        intent?.let { intent ->
            if (intent.hasExtra(EXTRA_DEEP_LINK_MODEL)) {
                (intent.getStringExtra(EXTRA_DEEP_LINK_MODEL))?.let { channelCode ->
                    Timber.d("Received channel code: $channelCode")
                    viewModel.joinChannel(channelCode, channel_surface.holder)
                }
                intent.removeExtra(EXTRA_DEEP_LINK_MODEL)
                return@launchMain
            }
        }
    }

}
