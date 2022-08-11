/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdebugmenu.common

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior

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
