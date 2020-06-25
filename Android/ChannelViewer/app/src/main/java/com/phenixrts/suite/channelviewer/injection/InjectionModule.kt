/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.channelviewer.injection

import com.phenixrts.suite.channelviewer.BuildConfig
import com.phenixrts.suite.channelviewer.ChannelViewerApplication
import com.phenixrts.suite.channelviewer.repositories.ChannelExpressRepository
import com.phenixrts.suite.phenixcommon.common.FileWriterDebugTree
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

private const val TIMBER_TAG = "ChannelViewer:"

@Module
class InjectionModule(private val context: ChannelViewerApplication) {

    @Singleton
    @Provides
    fun provideChannelExpressRepository(): ChannelExpressRepository = ChannelExpressRepository(context)

    @Provides
    @Singleton
    fun provideFileWriterDebugTree(): FileWriterDebugTree =
        FileWriterDebugTree(context, TIMBER_TAG, "${BuildConfig.APPLICATION_ID}.provider")

}
