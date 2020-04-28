/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.common

import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.phenixrts.suite.channelviewer.R
import kotlin.system.exitProcess

private const val QUIT_DELAY = 1000L

private fun AppCompatActivity.showToast(message: String) = launchMain {
    Toast.makeText(this@showToast, message, Toast.LENGTH_SHORT).show()
}

fun AppCompatActivity.closeApp() {
    showToast(getString(R.string.err_invalid_deep_link))
    Handler().postDelayed({
        finishAffinity()
        finishAndRemoveTask()
        exitProcess(0)
    }, QUIT_DELAY)
}
