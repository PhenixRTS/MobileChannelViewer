/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.injection

import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.channelviewer.ui.MainActivity
import com.phenixrts.suite.channelviewer.ui.SplashActivity
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [InjectionModule::class])
interface InjectionComponent {
    fun inject(target: ChannelViewerApplication)
    fun inject(target: MainActivity)
    fun inject(target: SplashActivity)
    fun inject(target: ChannelExpressRepository)
}
