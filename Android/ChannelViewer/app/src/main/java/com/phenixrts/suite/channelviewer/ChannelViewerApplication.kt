/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer

import android.app.Application
import com.phenixrts.suite.channelviewer.injection.DaggerInjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionModule
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import timber.log.Timber
import javax.inject.Inject

class ChannelViewerApplication : Application() {

    @Inject
    lateinit var fileWriterTree: FileWriterDebugTree

    override fun onCreate() {
        super.onCreate()

        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
        component.inject(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(fileWriterTree)
        }
    }

    companion object {
        lateinit var component: InjectionComponent
            private set
    }
}
