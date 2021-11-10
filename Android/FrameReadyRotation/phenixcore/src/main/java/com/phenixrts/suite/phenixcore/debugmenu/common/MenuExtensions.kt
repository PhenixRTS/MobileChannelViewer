/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.debugmenu.common

import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.phenixrts.suite.phenixcore.common.launchMain

fun FragmentActivity.showToast(message: String) {
    if (message.isNotBlank()) {
        launchMain {
            Toast.makeText(this@showToast, message, Toast.LENGTH_SHORT).show()
        }
    }
}

fun BottomSheetBehavior<View>.isOpened() = state == BottomSheetBehavior.STATE_EXPANDED

fun BottomSheetBehavior<View>.open() {
    if (state != BottomSheetBehavior.STATE_EXPANDED) {
        state = BottomSheetBehavior.STATE_EXPANDED
    }
}

fun BottomSheetBehavior<View>.hide() {
    if (state != BottomSheetBehavior.STATE_HIDDEN) {
        state = BottomSheetBehavior.STATE_HIDDEN
    }
}
