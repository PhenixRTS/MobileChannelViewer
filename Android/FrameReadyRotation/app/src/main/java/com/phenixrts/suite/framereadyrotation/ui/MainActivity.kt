/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.framereadyrotation.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.phenixrts.suite.framereadyrotation.BuildConfig
import com.phenixrts.suite.framereadyrotation.FrameReadyApplication
import com.phenixrts.suite.framereadyrotation.R
import com.phenixrts.suite.framereadyrotation.common.showInvalidDeepLinkDialog
import com.phenixrts.suite.framereadyrotation.common.lazyViewModel
import com.phenixrts.suite.framereadyrotation.databinding.ActivityMainBinding
import com.phenixrts.suite.framereadyrotation.ui.viewmodel.ChannelViewModel
import com.phenixrts.suite.phenixcore.debugmenu.common.showToast
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.debugmenu.common.hide
import com.phenixrts.suite.phenixcore.debugmenu.common.isOpened
import com.phenixrts.suite.phenixcore.debugmenu.common.open
import com.phenixrts.suite.phenixcore.repositories.models.PhenixChannelState
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixcore.repositories.models.PhenixEvent
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject lateinit var phenixCore: PhenixCore
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsMenu: BottomSheetBehavior<View>

    private val viewModel: ChannelViewModel by lazyViewModel({ application as FrameReadyApplication }) {
        ChannelViewModel(phenixCore)
    }

    private val menuStateListener = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            binding.settingsBackground.root.visibility = View.VISIBLE
            binding.settingsBackground.root.alpha = slideOffset
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding.settingsBackground.root.visibility = View.GONE
                binding.settingsBackground.root.alpha = 0f
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FrameReadyApplication.component.inject(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsMenu = BottomSheetBehavior.from(binding.settingsMenu.root)
        settingsMenu.addBottomSheetCallback(menuStateListener)

        binding.settings.setOnClickListener {
            Timber.d("Settings clicked")
            if (settingsMenu.isOpened()) {
                settingsMenu.hide()
            } else {
                settingsMenu.open()
            }
        }

        binding.settingsBackground.root.setOnClickListener {
            settingsMenu.hide()
        }

        binding.settingsMenu.applyChanges.setOnClickListener {
            val rotation = binding.settingsMenu.rotation.text.toString().toFloatOrNull() ?: 0f
            val width = binding.settingsMenu.width.text.toString().toIntOrNull() ?: 0
            val height = binding.settingsMenu.height.text.toString().toIntOrNull() ?: 0
            viewModel.applyChanges(binding.channelImage, rotation, width, height)
            settingsMenu.hide()
        }

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
                    viewModel.renderOnImage(binding.channelImage)
                    viewModel.subscribeToClosedCaptions(binding.closedCaptionView)
                    binding.offlineView.root.visibility = View.GONE
                } else {
                    binding.offlineView.root.visibility = View.VISIBLE
                }
            }
        }

        viewModel.renderOnImage(binding.channelImage)
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
        settingsMenu.removeBottomSheetCallback(menuStateListener)
    }

    override fun onBackPressed() {
        if (settingsMenu.isOpened()) {
            settingsMenu.hide()
            return
        }
        if (binding.debugMenu.isClosed()) {
            super.onBackPressed()
        }
    }
}
