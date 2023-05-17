/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.phenixrts.common.AuthenticationStatus
import com.phenixrts.pcast.AspectRatioMode
import com.phenixrts.pcast.Dimensions
import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.R
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

        if (isPictureInPictureAvailable()) {
            binding.checkboxPipEnabled.isChecked = true
        } else {
            binding.checkboxPipEnabled.visibility = View.GONE
        }

        launchMain {
            viewModel.onChannelExpressError.collect{
                Timber.d("Channel Express failed")
                showInvalidDeepLinkDialog()
            }
        }

        launchMain {
            viewModel.onChannelStreamPlaying.collect { state ->
                if (state.streamPlaying) {
                    binding.offlineView.root.visibility = View.GONE
                } else {
                    binding.offlineView.root.visibility = View.VISIBLE
                }
            }
        }

        launchMain {
            viewModel.onChannelStreamPlaying.collect { state ->
                if (state.streamPlaying) {
                    binding.offlineView.root.visibility = View.GONE
                } else {
                    binding.offlineView.root.visibility = View.VISIBLE
                }
            }
        }

        launchMain {
            viewModel.onVideoDisplayOptionsChanged.collect() { options ->
                updatePictureInPictureParameters(options.dimensions, options.aspectRatioMode);
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

    private fun updatePictureInPictureParameters(videoDimensions: Dimensions, aspectRatioMode: AspectRatioMode) {
        if (!isPictureInPictureAvailable()) {
            return
        }

        val pictureInPictureParametersBuilder = PictureInPictureParams.Builder()

        val videoAspectRatio = Rational(videoDimensions.width.toInt(), videoDimensions.height.toInt())

        val surfaceGlobalVisibleRectangle = Rect()
        binding.channelSurface.getGlobalVisibleRect(surfaceGlobalVisibleRectangle)

        // We need a rectangle to indicate which area to show while going into PiP mode.
        var videoDisplayRectangle = Rect()

        // In LetterBox ratio mode, the video takes up the whole width of the rendering surface.
        // Calculate the height based on the aspect ratio.
        if (aspectRatioMode == AspectRatioMode.LETTERBOX) {
            val videoActualHeight = surfaceGlobalVisibleRectangle.width() * videoAspectRatio.denominator / videoAspectRatio.numerator

            videoDisplayRectangle.top =
                (surfaceGlobalVisibleRectangle.centerY() - videoActualHeight / 2).coerceAtLeast(surfaceGlobalVisibleRectangle.top)
            videoDisplayRectangle.bottom = (videoDisplayRectangle.top + videoActualHeight).coerceAtMost(surfaceGlobalVisibleRectangle.bottom)
            videoDisplayRectangle.left = surfaceGlobalVisibleRectangle.left
            videoDisplayRectangle.right = surfaceGlobalVisibleRectangle.right
        }
        // In Fill ratio mode, the video takes the entire rendering area.
        // Just take the rectangle of the whole surface.
        else if (aspectRatioMode == AspectRatioMode.FILL) {
            videoDisplayRectangle = surfaceGlobalVisibleRectangle
        }

        if (!videoDisplayRectangle.isEmpty) {
            pictureInPictureParametersBuilder.setSourceRectHint(videoDisplayRectangle)
            pictureInPictureParametersBuilder.setAspectRatio(Rational(videoDisplayRectangle.width(), videoDisplayRectangle.height()))
        }

        setPictureInPictureParams(pictureInPictureParametersBuilder.build())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (isPictureInPictureAvailable() && binding.checkboxPipEnabled.isChecked) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            binding.closedCaptionView.visibility = View.GONE
            binding.checkboxPipEnabled.visibility = View.GONE
        } else {
            binding.closedCaptionView.visibility = View.VISIBLE
            binding.checkboxPipEnabled.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.debugMenu.onStop()
    }

    private fun isPictureInPictureAvailable() : Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }
}
