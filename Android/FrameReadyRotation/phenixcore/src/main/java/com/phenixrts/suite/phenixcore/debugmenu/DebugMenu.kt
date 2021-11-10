/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.debugmenu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.phenixrts.express.RoomExpress
import com.phenixrts.suite.phenixcore.R
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.databinding.ViewDebugLayoutBinding
import com.phenixrts.suite.phenixcore.debugmenu.common.FileWriterDebugTree
import com.phenixrts.suite.phenixcore.debugmenu.common.hide
import com.phenixrts.suite.phenixcore.debugmenu.common.isOpened
import com.phenixrts.suite.phenixcore.debugmenu.common.open
import timber.log.Timber
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DebugMenu: CoordinatorLayout {

    private var fileWriterDebugTree: FileWriterDebugTree? = null
    private var roomExpress: RoomExpress? = null
    private val filesToLog = arrayListOf<Uri>()
    private var onShow: () -> Unit = {}
    private var onError: () -> Unit = {}
    private var providerAuthority: String = ""

    private lateinit var binding: ViewDebugLayoutBinding
    private lateinit var debugMenu: BottomSheetBehavior<View>

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

    fun observeDebugMenu(debugTree: FileWriterDebugTree, express: RoomExpress?, authority: String,
                         onShow: () -> Unit, onError: () -> Unit) {
        fileWriterDebugTree = debugTree
        roomExpress = express
        providerAuthority = authority
        this.onShow = onShow
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

    fun onStart(appVersion: String, sdkVersion: String) {
        debugMenu.addBottomSheetCallback(menuStateListener)
        binding.debugMenu.debugClose.setOnClickListener { hideDebugMenu() }
        binding.debugMenu.debugAppVersion.text = appVersion
        binding.debugMenu.debugSdkVersion.text = sdkVersion
        binding.debugMenu.debugShare.setOnClickListener { shareLogs() }
    }

    fun onStop() {
        debugMenu.removeBottomSheetCallback(menuStateListener)
    }

    fun isClosed(): Boolean {
        if (debugMenu.isOpened()) {
            debugMenu.hide()
            return false
        }
        return true
    }

    private fun initView() {
        binding = ViewDebugLayoutBinding.bind(View.inflate(context, R.layout.view_debug_layout, this))
        debugMenu = BottomSheetBehavior.from(binding.debugMenu.root)
        binding.root.setOnClickListener {
            onScreenTapped()
        }
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

    private fun showDebugMenu() {
        Timber.d("Showing debug menu")
        debugMenu.open()
    }

    private fun hideDebugMenu() {
        Timber.d("Hiding bottom menu")
        debugMenu.hide()
    }

    private fun shareLogs() = launchMain {
        try {
            Timber.d("Share Logs clicked")
            filesToLog.clear()
            fileWriterDebugTree?.writeSdkLogs(collectLogMessages())
            filesToLog.addAll(fileWriterDebugTree?.getLogFileUris(providerAuthority) ?: emptyList())
            if (filesToLog.isNotEmpty()) {
                onShow()
            } else {
                onError()
            }
        } catch (e: Exception) {
            onError()
        }
    }

    private suspend fun collectLogMessages(): String = suspendCoroutine { continuation ->
        roomExpress?.pCastExpress?.pCast?.collectLogMessages { _, _, messages ->
            continuation.resume(messages)
        } ?: continuation.resume("")
    }

    private fun onScreenTapped() {
        val currentTapTime = System.currentTimeMillis()
        if (currentTapTime - lastTapTime <= TAP_DELTA) {
            tapCount++
        } else {
            tapCount = 0
        }
        lastTapTime = currentTapTime
        if (tapCount == TAP_GOAL) {
            tapCount = 0
            Timber.d("Debug menu unlocked")
            showDebugMenu()
        }
    }

    private companion object {
        private const val INTENT_CHOOSER_TYPE = "text/plain"
        private const val TAP_DELTA = 250L
        private const val TAP_GOAL = 5
    }
}
