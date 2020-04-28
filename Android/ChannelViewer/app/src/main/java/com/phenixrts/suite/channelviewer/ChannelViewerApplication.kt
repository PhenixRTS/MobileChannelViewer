/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer

import android.app.Application
import com.phenixrts.suite.channelviewer.common.LineNumberDebugTree
import com.phenixrts.suite.channelviewer.injection.DaggerInjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionModule
import timber.log.Timber

private const val TIMBER_TAG = "ChannelViewer:"

class ChannelViewerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
        if (BuildConfig.DEBUG) {
            Timber.plant(LineNumberDebugTree(TIMBER_TAG))
        }
    }

    companion object {
        lateinit var component: InjectionComponent
            private set
    }
}
