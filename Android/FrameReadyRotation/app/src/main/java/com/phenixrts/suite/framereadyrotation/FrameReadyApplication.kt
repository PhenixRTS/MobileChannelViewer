/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.framereadyrotation

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.phenixrts.suite.framereadyrotation.injection.DaggerInjectionComponent
import com.phenixrts.suite.framereadyrotation.injection.InjectionComponent
import com.phenixrts.suite.framereadyrotation.injection.InjectionModule

class FrameReadyApplication : Application(), ViewModelStoreOwner {

    private val appViewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override fun onCreate() {
        super.onCreate()
        component = DaggerInjectionComponent.builder().injectionModule(InjectionModule(this)).build()
    }

    override fun getViewModelStore() = appViewModelStore

    companion object {
        lateinit var component: InjectionComponent
            private set
    }
}
