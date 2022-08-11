/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdebugmenu

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixdebugmenu.common.hide
import com.phenixrts.suite.phenixdebugmenu.common.isOpened
import com.phenixrts.suite.phenixdebugmenu.common.open
import com.phenixrts.suite.phenixdebugmenu.databinding.ViewDebugLayoutBinding
import com.phenixrts.suite.phenixdebugmenu.models.DebugEvent
import kotlinx.coroutines.delay
import timber.log.Timber
import java.lang.Exception

class DebugMenu: CoordinatorLayout {

    private var fileWriterDebugTree: FileWriterDebugTree? = null
    private val filesToLog = arrayListOf<Uri>()
    private var onEvent: (DebugEvent) -> Unit = {}
    private var onError: (String) -> Unit = {}
    private var providerAuthority: String = ""
    private var sdkVersion: String = ""

    private lateinit var binding: ViewDebugLayoutBinding
    private lateinit var debugMenu: BottomSheetBehavior<View>

    private val adapter by lazy { LogAdapter() }

    private var lastTapTime = System.currentTimeMillis()
    private var tapCount = 0

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView()
    }

    fun observeDebugMenu(
        phenixCore: PhenixCore,
        authority: String,
        onEvent: (DebugEvent) -> Unit,
        onError: (String) -> Unit
    ) {
        fileWriterDebugTree = phenixCore.debugTree
        providerAuthority = authority
        sdkVersion = context.getString(
            R.string.debug_sdk_version,
            phenixCore.sdkVersion,
            phenixCore.sdkCode
        )
        this.onEvent = onEvent
        this.onError = onError
    }

    fun showAppChooser(activity: FragmentActivity) =
        launchMain {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesToLog)
                type = INTENT_CHOOSER_TYPE
            }
            activity.startActivity(
                Intent.createChooser(
                    intent,
                    activity.getString(R.string.debug_share_app_logs)
                )
            )
        }

    fun onStart(appVersion: String, appCode: String) {
        debugMenu.addBottomSheetCallback(menuStateListener)
        binding.debugMenu.debugClose.setOnClickListener { hide() }
        binding.debugMenu.debugAppVersion.text = context.getString(
            R.string.debug_app_version,
            appVersion,
            appCode
        )
        binding.debugMenu.debugSdkVersion.text = sdkVersion
        binding.debugMenu.debugShare.setOnClickListener { shareLogs() }
        binding.debugMenu.debugClear.setOnClickListener { clearLogs() }
        binding.debugMenu.debugShowApp.setOnClickListener { showLogs(showAppLogs = true) }
        binding.debugMenu.debugShowSdk.setOnClickListener { showLogs(showAppLogs = false) }
        binding.debugMenu.debugBack.setOnClickListener { hideLogs() }
    }

    fun onStop() {
        debugMenu.removeBottomSheetCallback(menuStateListener)
    }

    fun isOpened() = debugMenu.isOpened()

    fun hide() {
        Timber.d("Hiding debug menu")
        debugMenu.hide()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        binding = ViewDebugLayoutBinding.bind(View.inflate(context, R.layout.view_debug_layout, this))
        debugMenu = BottomSheetBehavior.from(binding.debugMenu.root)
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onScreenTapped()
            }
            false
        }
        binding.debugMenu.debugLogs.adapter = adapter
    }

    private val menuStateListener = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            binding.debugBackground.root.visibility = View.VISIBLE
            binding.debugBackground.root.alpha = slideOffset
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding.debugBackground.root.visibility = View.GONE
                binding.debugBackground.root.alpha = 0f
            }
        }
    }

    private fun show() {
        Timber.d("Showing debug menu")
        debugMenu.open()
    }

    private fun showLogs(showAppLogs: Boolean) {
        binding.debugMenu.debugLogContainer.visibility = View.VISIBLE
        binding.debugMenu.debugMenuButtons.visibility = View.GONE

        fileWriterDebugTree?.collectLogs(showAppLogs) { logs ->
            launchMain {
                adapter.items = logs
                delay(200)
                binding.debugMenu.debugLogs.smoothScrollToPosition(adapter.itemCount)
            }
        }
    }

    private fun hideLogs() {
        binding.debugMenu.debugLogContainer.visibility = View.GONE
        binding.debugMenu.debugMenuButtons.visibility = View.VISIBLE
    }

    private fun shareLogs() = launchMain {
        try {
            Timber.d("Share Logs clicked")
            filesToLog.clear()
            fileWriterDebugTree?.writeSdkLogs()
            filesToLog.addAll(fileWriterDebugTree?.getLogFileUris(providerAuthority) ?: emptyList())
            if (filesToLog.isNotEmpty()) {
                onEvent(DebugEvent.SHOW_MENU)
            } else {
                onError(context.getString(R.string.debug_error_share_logs_failed))
            }
        } catch (e: Exception) {
            onError(context.getString(R.string.debug_error_share_logs_failed))
        }
    }

    private fun clearLogs() = launchMain {
        try {
            fileWriterDebugTree?.clearLogs()
            onEvent(DebugEvent.FILES_DELETED)
        } catch (e: Exception) {
            onError(context.getString(R.string.debug_error_clear_logs_failed))
        }
    }

    private fun onScreenTapped() {
        val currentTapTime = System.currentTimeMillis()
        if (currentTapTime - lastTapTime <= TAP_DELTA) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTime = currentTapTime
        if (tapCount == TAP_GOAL) {
            tapCount = 0
            Timber.d("Debug menu unlocked")
            show()
        }
    }

    private companion object {
        private const val INTENT_CHOOSER_TYPE = "text/plain"
        private const val TAP_DELTA = 250L
        private const val TAP_GOAL = 5
    }
}
