/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.phenixrts.suite.channelviewer.injection.DaggerInjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionModule
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import timber.log.Timber
import javax.inject.Inject

class ChannelViewerApplication : Application(), ViewModelStoreOwner {

    @Inject
    lateinit var fileWriterTree: FileWriterDebugTree

    private val appViewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override fun onCreate() {
        super.onCreate()

        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
        component.inject(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(fileWriterTree)
        }
    }

    override fun getViewModelStore() = appViewModelStore

    companion object {
        lateinit var component: InjectionComponent
            private set
    }
}
