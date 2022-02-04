/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.phenixrts.suite.channelviewer.common.LineNumberDebugTree
import com.phenixrts.suite.channelviewer.injection.DaggerInjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionComponent
import com.phenixrts.suite.channelviewer.injection.InjectionModule
import timber.log.Timber

class ChannelViewerApplication : Application(), ViewModelStoreOwner {

    private val appViewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(LineNumberDebugTree("ChannelViewer"))
        }
        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
    }

    override fun getViewModelStore() = appViewModelStore

    companion object {
        lateinit var component: InjectionComponent
            private set
    }
}
