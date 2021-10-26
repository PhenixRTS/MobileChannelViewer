/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.injection

import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.phenixcore.PhenixCore
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class InjectionModule(private val context: ChannelViewerApplication) {

    @Singleton
    @Provides
    fun providePhenixCore() = PhenixCore(context)

}
